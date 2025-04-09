package com.atguigu.daijia.coupon.service;

import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

public interface CouponInfoService extends IService<CouponInfo> {


    //查询未领取优惠券分页列表
    PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId);

    //查询未使用优惠券分页列表
    PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId);

    //查询已使用优惠券分页列表
    PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId);

    //领取优惠券
    Boolean receive(Long customerId, Long couponId);

    //获取未使用的最佳优惠券信息
    List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount);

    //使用优惠券
    BigDecimal useCoupon(UseCouponForm useCouponForm);
}
