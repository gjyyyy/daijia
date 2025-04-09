package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderBill;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderProfitsharing;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderBillMapper orderBillMapper;

    @Autowired
    private OrderProfitsharingMapper orderProfitsharingMapper;

    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm,orderInfo);
        //订单号
        String orderNo = UUID.randomUUID().toString().replaceAll("-","");
        orderInfo.setOrderNo(orderNo);
        //订单状态
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());

        orderInfoMapper.insert(orderInfo);

        //生成订单之后，发送延迟消息
        this.sendDelayMessage(orderInfo.getId());

        this.log(orderInfo.getId(),orderInfo.getStatus());

        //存入订单标识进redis，防止抢单时大量访问数据库
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK+orderInfo.getId(),
                "0",RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);

        return orderInfo.getId();
    }

    //生成订单之后，发送延迟消息
    private void sendDelayMessage(Long orderId) {
        try {
            RBlockingQueue<String> queue_cancel =
                    redissonClient.getBlockingQueue("queue_cancel");

            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(queue_cancel);

            //3 发送消息到延迟队列里面
            //设置过期时间
            delayedQueue.offer(orderId.toString(), 15, TimeUnit.MINUTES);
        }catch (Exception e){
            e.printStackTrace();
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }


    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }

    //查询订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,orderId);
        wrapper.select(OrderInfo::getStatus);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        //订单不存在
        if(orderInfo == null) {
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    //司机抢单
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        //判断redis中是否还有订单标识
        Boolean isHas = redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        if(!isHas){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //创建锁
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);

        try{
            boolean flag = lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME, RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if(flag){

                if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId)){
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }
                LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(OrderInfo::getId,orderId);
                OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

                //司机抢单
                //修改order_info表订单状态值2：已经接单 + 司机id + 司机接单时间
                //修改条件：根据订单id
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setDriverId(driverId);
                orderInfo.setAcceptTime(new Date());
                int rows = orderInfoMapper.updateById(orderInfo);
                if(rows != 1){
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
            }
        }catch (Exception e){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }finally {
            lock.unlock();
        }
        return true;
    }

    //乘客端查找当前订单
    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        //封装条件
        //乘客id
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getCustomerId,customerId);

        //各种状态
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(),
                OrderStatus.UNPAID.getStatus()
        };
        wrapper.in(OrderInfo::getStatus,statusArray);

        //获取最新一条记录
        wrapper.orderByDesc(OrderInfo::getId);
        wrapper.last("limit 1");

        //调用方法
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        //封装到CurrentOrderInfoVo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if(orderInfo != null) {
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    //司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        //封装条件
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getDriverId,driverId);
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus()
        };
        wrapper.in(OrderInfo::getStatus,statusArray);
        wrapper.orderByDesc(OrderInfo::getId);
        wrapper.last(" limit 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        //封装到vo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if(null != orderInfo) {
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    //司机到达起始点
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OrderInfo::getId,orderId);
        wrapper.eq(OrderInfo::getDriverId,driverId);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setArriveTime(new Date());
        orderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if(rows == 1) return true;
        else throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
    }

    //更新代驾车辆信息
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,updateOrderCartForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId,updateOrderCartForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm,orderInfo);
        orderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());
        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if(rows == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    //开始代驾服务
    @Override
    public Boolean startDriver(StartDriveForm startDriveForm) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,startDriveForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId,startDriveForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        orderInfo.setStartServiceTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if(rows == 1) return true;
        else throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
    }

    //根据时间段获取当前司机订单数
    @Override
    public Long getOrderNumByTime(String startTime, String endTime, Long driverId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getDriverId,driverId);
        wrapper.ge(OrderInfo::getStartServiceTime,startTime);
        wrapper.lt(OrderInfo::getStartServiceTime,endTime);

        Long count = orderInfoMapper.selectCount(wrapper);
        return count;
    }

    //结束代驾服务更新订单账单
    @Override
    public Boolean endDrive(UpdateOrderBillForm updateOrderBillForm) {
        //1 更新订单信息
        // update order_info set ..... where id=? and driver_id=?
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,updateOrderBillForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId,updateOrderBillForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        orderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        orderInfo.setFavourFee(updateOrderBillForm.getFavourFee());
        orderInfo.setRealDistance(updateOrderBillForm.getRealDistance());
        orderInfo.setEndServiceTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if(rows == 1){
            //添加账单数据
            OrderBill orderBill = new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm,orderBill);
            orderBill.setOrderId(updateOrderBillForm.getOrderId());
            orderBill.setPayAmount(updateOrderBillForm.getTotalAmount());
            orderBillMapper.insert(orderBill);

            //添加分账信息
            LambdaQueryWrapper<OrderProfitsharing> orderProfitsharingWrapper = new LambdaQueryWrapper<>();
            orderProfitsharingWrapper.orderByDesc(OrderProfitsharing::getId);
            orderProfitsharingWrapper.last("limit 1");
            OrderProfitsharing profitsharing = orderProfitsharingMapper.selectOne(orderProfitsharingWrapper);
            Long id = 1L;
            if(profitsharing != null){
                id += profitsharing.getId();
            }
            log.info("--------------------"+id+"---------------");
            OrderProfitsharing orderProfitsharing = new OrderProfitsharing();
            BeanUtils.copyProperties(updateOrderBillForm, orderProfitsharing);
            orderProfitsharing.setId(id);
            orderProfitsharing.setOrderId(updateOrderBillForm.getOrderId());
            orderProfitsharing.setRuleId(updateOrderBillForm.getProfitsharingRuleId());
            orderProfitsharing.setStatus(1);
            log.info("--------------------"+orderProfitsharing.getOrderId()+"---------------");
            orderProfitsharingMapper.insert(orderProfitsharing);
        }else{
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }

        return true;
    }

    //获取乘客订单分页列表
    @Override
    public PageVo findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectCustomerOrderPage(pageParam,customerId);
        return new PageVo(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }

    //获取司机订单分页列表
    @Override
    public PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectDriverOrderPage(pageParam,driverId);
        return new PageVo<>(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }

    //根据订单id获取实际账单信息
    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        LambdaQueryWrapper<OrderBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderBill::getOrderId,orderId);

        OrderBill orderBill = orderBillMapper.selectOne(wrapper);
        OrderBillVo orderBillVo = new OrderBillVo();
        BeanUtils.copyProperties(orderBill,orderBillVo);
        return orderBillVo;
    }

    //根据订单id获取实际分账信息
    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        LambdaQueryWrapper<OrderProfitsharing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderProfitsharing::getOrderId,orderId);
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(wrapper);

        OrderProfitsharingVo orderProfitsharingVo = new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing,orderProfitsharingVo);
        return orderProfitsharingVo;
    }

    //发送账单信息
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if(row == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    //获取订单支付信息
    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
        OrderPayVo orderPayVo = orderInfoMapper.selectOrderPayVo(orderNo,customerId);
        if(orderPayVo != null){
            String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
            orderPayVo.setContent(content);
        }
        return orderPayVo;
    }

    //更改订单支付状态
    @Override
    public Boolean updateOrderPayStatus(String orderNo) {
        //1 根据订单编号查询，判断订单状态
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo,orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        if(orderInfo == null || orderInfo.getStatus() == OrderStatus.PAID.getStatus()) {
            return true;
        }
        //2 更新状态
        LambdaQueryWrapper<OrderInfo> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(OrderInfo::getOrderNo,orderNo);

        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.PAID.getStatus());
        updateOrderInfo.setPayTime(new Date());

        int rows = orderInfoMapper.update(updateOrderInfo, updateWrapper);

        if(rows == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    //获取订单的系统奖励
    @Override
    public OrderRewardVo getOrderRewardFee(String orderNo) {
        //根据订单编号查询订单表
        OrderInfo orderInfo =
                orderInfoMapper.selectOne(
                        new LambdaQueryWrapper<OrderInfo>()
                                .eq(OrderInfo::getOrderNo, orderNo)
                                .select(OrderInfo::getId,OrderInfo::getDriverId));

        //根据订单id查询系统奖励表
        OrderBill orderBill =
                orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>()
                        .eq(OrderBill::getOrderId, orderInfo.getId())
                        .select(OrderBill::getRewardFee));

        OrderRewardVo orderRewardVo = new OrderRewardVo();
        orderRewardVo.setRewardFee(orderBill.getRewardFee());
        orderRewardVo.setOrderId(orderInfo.getId());
        orderRewardVo.setDriverId(orderInfo.getDriverId());
        return orderRewardVo;
    }

    //取消订单
    @Override
    public void orderCancel(long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(orderInfo.getStatus() == OrderStatus.WAITING_ACCEPT.getStatus()){
            orderInfo.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            int rows = orderInfoMapper.updateById(orderInfo);

            if(rows == 1){
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK+orderId);
            }
        }

    }

    //更新订单优惠券金额
    @Override
    public Boolean updateCouponAmount(Long orderId, BigDecimal couponAmount) {
        orderBillMapper.updateCouponAmount(orderId,couponAmount);
        return true;
    }

    //司机抢单(乐观锁)
    public Boolean robNewOrder1(Long driverId, Long orderId) {
        //判断redis中是否还有订单标识
        Boolean isHas = redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        if(!isHas){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //# 版本号(用status当作版本号)
        //update order_info set status =2 ,driver_id = ?,accept_time = ? where id=? and status = 1
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId,orderId);
        wrapper.eq(OrderInfo::getStatus,OrderStatus.WAITING_ACCEPT);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setDriverId(driverId);
        orderInfo.setAcceptTime(new Date());
        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if(rows != 1){
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        return true;
    }
}
