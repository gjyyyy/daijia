package com.atguigu.daijia.coupon.service.impl;

import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Autowired
    private CouponInfoMapper couponInfoMapper;

    @Autowired
    private CustomerCouponMapper customerCouponMapper;

    @Autowired
    private RedissonClient redissonClient;

    //查询未领取优惠券分页列表
    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    //查询未使用优惠券分页列表
    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    //查询已使用优惠券分页列表
    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    //领取优惠券
    @Override
    public Boolean receive(Long customerId, Long couponId) {
        //1、查询优惠券
        CouponInfo couponInfo = this.getById(couponId);
        if(null == couponInfo) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //2、优惠券过期日期判断
        if(couponInfo.getExpireTime().before(new Date())){
            throw new GuiguException(ResultCodeEnum.COUPON_EXPIRE);
        }

        //3、校验库存，优惠券领取数量判断
        if (couponInfo.getPublishCount() !=0 && couponInfo.getReceiveCount() >= couponInfo.getPublishCount()) {
            throw new GuiguException(ResultCodeEnum.COUPON_LESS);
        }

        RLock lock = null;
        try {
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME,
                    RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if(flag){
                //4、校验每人限领数量
                if (couponInfo.getPerLimit() > 0) {
                    LambdaQueryWrapper<CustomerCoupon> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CustomerCoupon::getCouponId,couponId);
                    wrapper.eq(CustomerCoupon::getCustomerId,customerId);

                    Long count = customerCouponMapper.selectCount(wrapper);
                    //4.2、校验限领数量
                    if (count >= couponInfo.getPerLimit()) {
                        throw new GuiguException(ResultCodeEnum.COUPON_USER_LIMIT);
                    }
                }

                //5、更新优惠券领取数量
                int row = couponInfoMapper.updateReceiveCount(couponId);
                if (row == 1) {
                    //6、保存领取记录
                    this.saveCustomerCoupon(customerId, couponId, couponInfo.getExpireTime());
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != lock) {
                lock.unlock();
            }
        }
        return true;
    }

    //获取未使用的最佳优惠券信息
    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        //1.定义符合条件的优惠券信息容器
        List<AvailableCouponVo> availableCouponVoList = new ArrayList<>();

        List<NoUseCouponVo> list = couponInfoMapper.findNoUseList(customerId);

        list.forEach(noUseCouponVo->{
            if(noUseCouponVo.getCouponType().intValue() == 1){
                //2.1.现金券
                //使用门槛判断
                //2.1.1.没门槛，订单金额必须大于优惠券减免金额
                //减免金额
                BigDecimal reduceAmount = noUseCouponVo.getAmount();
                if (noUseCouponVo.getConditionAmount().doubleValue() == 0 && orderAmount.subtract(reduceAmount).doubleValue() >= 0) {
                    availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
                }
                //2.1.2.有门槛，订单金额大于优惠券门槛金额
                if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && orderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() >= 0) {
                    availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
                }
            } else {
                //2.2.折扣券
                //使用门槛判断
                //订单折扣后金额
                BigDecimal discountOrderAmount = orderAmount.multiply(noUseCouponVo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
                //减免金额
                BigDecimal reduceAmount = orderAmount.subtract(discountOrderAmount);
                //订单优惠金额
                //2.2.1.没门槛
                if (noUseCouponVo.getConditionAmount().doubleValue() == 0) {
                    availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
                }
                //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
                if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() >= 0) {
                    availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
                }
            }

        });

        if(!CollectionUtils.isEmpty(availableCouponVoList)){
            Collections.sort(availableCouponVoList, new Comparator<AvailableCouponVo>() {
                @Override
                public int compare(AvailableCouponVo o1, AvailableCouponVo o2) {
                    return o1.getReduceAmount().compareTo(o2.getReduceAmount());
                }
            });
        }
        return availableCouponVoList;
    }

    //使用优惠券
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        //1 根据乘客优惠券id获取乘客优惠卷信息
        CustomerCoupon customerCoupon =
                customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if(customerCoupon == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //2 根据优惠卷id获取优惠卷信息
        CouponInfo couponInfo =
                couponInfoMapper.selectById(customerCoupon.getCouponId());
        if(couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //判断该优惠券是否为乘客所有
        if(customerCoupon.getCustomerId().longValue() != useCouponForm.getCustomerId().longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //获取优惠券减免金额
        BigDecimal reduceAmount = null;
        if(couponInfo.getCouponType().intValue() == 1) {
            //使用门槛判断
            //2.1.1.没门槛，订单金额必须大于优惠券减免金额
            if (couponInfo.getConditionAmount().doubleValue() == 0 && useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue() >= 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
            //2.1.2.有门槛，订单金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue() >= 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
        } else {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount = useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //订单优惠金额
            //2.2.1.没门槛
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue() >= 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
        }

        if(reduceAmount.doubleValue() > 0) {
            int row = couponInfoMapper.updateUseCount(couponInfo.getId());
            if(row == 1) {
                CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
                updateCustomerCoupon.setId(customerCoupon.getId());
                updateCustomerCoupon.setUsedTime(new Date());
                updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
                updateCustomerCoupon.setStatus(2);
                customerCouponMapper.updateById(updateCustomerCoupon);
                return reduceAmount;
            }
        }
        throw new GuiguException(ResultCodeEnum.DATA_ERROR);
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal reduceAmount) {
        AvailableCouponVo bestNoUseCouponVo = new AvailableCouponVo();
        BeanUtils.copyProperties(noUseCouponVo, bestNoUseCouponVo);
        bestNoUseCouponVo.setCouponId(noUseCouponVo.getId());
        bestNoUseCouponVo.setReduceAmount(reduceAmount);
        return bestNoUseCouponVo;
    }

    //6、保存领取记录
    private void saveCustomerCoupon(Long customerId, Long couponId, Date expireTime) {
        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setCouponId(couponId);
        customerCoupon.setStatus(1);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setExpireTime(expireTime);
        customerCouponMapper.insert(customerCoupon);
    }
}
