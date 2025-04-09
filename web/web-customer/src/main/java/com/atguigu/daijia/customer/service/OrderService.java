package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.payment.CreateWxPaymentForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;

import java.util.List;

public interface OrderService {

    //计算订单信息
    ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm);

    //乘客下单
    Long submitOrder(SubmitOrderForm submitOrderForm);

    //查询订单状态
    Integer getOrderStatus(Long orderId);

    List<NewOrderDataVo> findNewOrderQueueData(Long driverId);

    //乘客查找当前订单
    CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId);

    //获取订单信息
    OrderInfoVo getOrderInfo(Long orderId, Long customerId);

    //根据订单id获取司机基本信息
    DriverInfoVo getDriverInfo(Long orderId, Long customerId);

    //司机赶往代驾起始点：获取订单经纬度位置
    OrderLocationVo getCacheOrderLocation(Long orderId);

    DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm);

    //代驾服务：获取订单服务最后一个位置信息
    OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId);

    //获取乘客订单分页列表
    PageVo findCustomerOrderPage(Long customerId, Long page, Long limit);

    //创建微信支付
    WxPrepayVo createWxPayment(CreateWxPaymentForm createWxPaymentForm);

    //支付状态查询
    Boolean queryPayStatus(String orderNo);
}
