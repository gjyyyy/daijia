package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    private NewOrderFeignClient newOrderFeignClient;

    @Autowired
    private MapFeignClient mapFeignClient;

    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private FeeRuleFeignClient feeRuleFeignClient;

    @Autowired
    private RewardRuleFeignClient rewardRuleFeignClient;

    @Autowired
    private ProfitsharingRuleFeignClient profitsharingRuleFeignClient;


    //查询订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        Result<Integer> orderStatusResult = orderInfoFeignClient.getOrderStatus(orderId);
        return orderStatusResult.getData();
    }
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        return newOrderFeignClient.findNewOrderQueueData(driverId).getData();
    }

    //司机抢单
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        return orderInfoFeignClient.robNewOrder(driverId,orderId).getData();
    }

    //司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        return orderInfoFeignClient.searchDriverCurrentOrder(driverId).getData();
    }

    //获取订单账单详细信息
    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if(orderInfo.getDriverId() != driverId) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //获取账单和分账信息
        OrderBillVo orderBillVo = null;
        OrderProfitsharingVo orderProfitsharingVo = null;
        if(orderInfo.getStatus() >= OrderStatus.END_SERVICE.getStatus()){
            orderBillVo = orderInfoFeignClient.getOrderBillInfo(orderId).getData();
            orderProfitsharingVo = orderInfoFeignClient.getOrderProfitsharing(orderId).getData();
        }
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo,orderInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setOrderProfitsharingVo(orderProfitsharingVo);
        return orderInfoVo;
    }

    //计算最佳驾驶线路
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }

    //司机到达代驾起始地点
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        //判断
        // orderInfo有代驾开始位置
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();

        //司机当前位置
        OrderLocationVo orderLocationVo = locationFeignClient.getCacheOrderLocation(orderId).getData();

        //司机当前位置 和 代驾开始位置距离
        double distance = LocationUtil.getDistance(orderInfo.getStartPointLatitude().doubleValue(),
                orderInfo.getStartPointLongitude().doubleValue(),
                orderLocationVo.getLatitude().doubleValue(),
                orderLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_START_LOCATION_DISTION_ERROR);
        }
        return orderInfoFeignClient.driverArriveStartLocation(orderId,driverId).getData();
    }

    //更新代驾车辆信息
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        return orderInfoFeignClient.updateOrderCart(updateOrderCartForm).getData();
    }

    //开始代驾服务
    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        return orderInfoFeignClient.startDrive(startDriveForm).getData();
    }

    //结束代驾
    @Override
    public Boolean endDrive(OrderFeeForm orderFeeForm) {
        //1 根据orderId获取订单信息，判断当前订单是否司机接单
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
        if(orderInfo.getDriverId() != orderFeeForm.getDriverId()){
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //防止刷单
        OrderServiceLastLocationVo orderServiceLastLocationVo = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();

        //司机当前位置 距离 结束代驾位置
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(),
                orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(),
                orderServiceLastLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_END_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        //2 计算订单实际里程
        BigDecimal realDistance = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();

        //3 计算代驾实际费用
        FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(realDistance);
        feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());

        //计算司机到达代驾开始位置时间
        //orderInfo.getArriveTime() - orderInfo.getAcceptTime()
        // 分钟 = 毫秒 / 1000 * 60
        Integer waitMinute =
                Math.abs((int)((orderInfo.getArriveTime().getTime()-orderInfo.getAcceptTime().getTime())/(1000 * 60)));
        feeRuleRequestForm.setWaitMinute(waitMinute);

        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();

        //实际费用 = 代驾费用 + 其他费用（停车费）
        BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount().
                            add(orderFeeForm.getParkingFee()).
                            add(orderFeeForm.getOtherFee()).
                            add(orderInfo.getFavourFee());
        feeRuleResponseVo.setTotalAmount(totalAmount);

        //4 计算系统奖励
        String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
        String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 23:59:59";
        Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime, orderFeeForm.getDriverId()).getData();

        RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
        rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
        rewardRuleRequestForm.setOrderNum(orderNum);
        RewardRuleResponseVo rewardRuleResponseVo =
                rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();

        //5 计算分账信息
        ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
        profitsharingRuleRequestForm.setOrderNum(orderNum);
        profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo =
                profitsharingRuleFeignClient.calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();


        //6 封装实体类，结束代驾更新订单，添加账单和分账信息
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee());

        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        //订单奖励信息
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        //代驾费用信息
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        //分账相关信息
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());
        orderInfoFeignClient.endDrive(updateOrderBillForm);
        return true;
    }

    //异步编排
    @SneakyThrows
    public Boolean endDriveThread(OrderFeeForm orderFeeForm) {
        //1 根据orderId获取订单信息，判断当前订单是否司机接单
        CompletableFuture<OrderInfo> orderInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
            if (orderInfo.getDriverId() != orderFeeForm.getDriverId()) {
                throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
            }
            return orderInfo;
        });

        CompletableFuture<OrderServiceLastLocationVo> orderServiceLastLocationVoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //防止刷单
            OrderServiceLastLocationVo orderServiceLastLocationVo =
                    locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();
            return orderServiceLastLocationVo;
        });

        CompletableFuture.allOf(orderInfoCompletableFuture,
                orderServiceLastLocationVoCompletableFuture).join();
        OrderInfo orderInfo = orderInfoCompletableFuture.get();
        OrderServiceLastLocationVo orderServiceLastLocationVo = orderServiceLastLocationVoCompletableFuture.get();

        //司机当前位置 距离 结束代驾位置
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(),
                orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(),
                orderServiceLastLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_END_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        //2 计算订单实际里程
        CompletableFuture<BigDecimal> realDistanceCompletableFuture = CompletableFuture.supplyAsync(() -> {
            BigDecimal realDistance = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();
            return realDistance;
        });


        //3 计算代驾实际费用
        CompletableFuture<FeeRuleResponseVo> feeRuleResponseVoCompletableFuture = realDistanceCompletableFuture.thenApplyAsync((realDistance) -> {
            FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
            feeRuleRequestForm.setDistance(realDistance);
            feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            //计算司机到达代驾开始位置时间
            //orderInfo.getArriveTime() - orderInfo.getAcceptTime()
            // 分钟 = 毫秒 / 1000 * 60
            Integer waitMinute =
                    Math.abs((int) ((orderInfo.getArriveTime().getTime() - orderInfo.getAcceptTime().getTime()) / (1000 * 60)));
            feeRuleRequestForm.setWaitMinute(waitMinute);

            FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();

            //实际费用 = 代驾费用 + 其他费用（停车费）
            BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount().
                    add(orderFeeForm.getParkingFee()).
                    add(orderFeeForm.getOtherFee()).
                    add(orderInfo.getFavourFee());
            feeRuleResponseVo.setTotalAmount(totalAmount);
            return feeRuleResponseVo;
        });

        //4 计算系统奖励
        CompletableFuture<Long> orderNumCompletableFuture = CompletableFuture.supplyAsync(() -> {
            String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
            String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 23:59:59";
            Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime, orderFeeForm.getDriverId()).getData();
            return orderNum;
        });

        CompletableFuture<RewardRuleResponseVo> rewardRuleResponseVoCompletableFuture = orderNumCompletableFuture.thenApplyAsync((orderNum) -> {
            RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
            rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            rewardRuleRequestForm.setOrderNum(orderNum);
            RewardRuleResponseVo rewardRuleResponseVo =
                    rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();
            return rewardRuleResponseVo;
        });


        CompletableFuture<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoCompletableFuture = feeRuleResponseVoCompletableFuture.thenCombineAsync(orderNumCompletableFuture, (feeRuleResponseVo, orderNum) -> {
            //5 计算分账信息
            ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
            profitsharingRuleRequestForm.setOrderNum(orderNum);
            profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
            ProfitsharingRuleResponseVo profitsharingRuleResponseVo =
                    profitsharingRuleFeignClient.calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();
            return profitsharingRuleResponseVo;
        });

        //合并
        CompletableFuture.allOf(
                orderInfoCompletableFuture,
                realDistanceCompletableFuture,
                feeRuleResponseVoCompletableFuture,
                orderNumCompletableFuture,
                rewardRuleResponseVoCompletableFuture,
                profitsharingRuleResponseVoCompletableFuture
        ).join();

        //获取执行结果
        BigDecimal realDistance = realDistanceCompletableFuture.get();
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoCompletableFuture.get();
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleResponseVoCompletableFuture.get();
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleResponseVoCompletableFuture.get();

        //6 封装实体类，结束代驾更新订单，添加账单和分账信息
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee());

        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        //订单奖励信息
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        //代驾费用信息
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        //分账相关信息
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());
        orderInfoFeignClient.endDrive(updateOrderBillForm);
        return true;
    }

    //获取司机订单分页列表
    @Override
    public PageVo findDriverOrderPage(Long driverId, Long page, Long limit) {
        return orderInfoFeignClient.findDriverOrderPage(driverId,page,limit).getData();
    }

    //司机发送账单信息
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        return orderInfoFeignClient.sendOrderBillInfo(orderId,driverId).getData();
    }
}
