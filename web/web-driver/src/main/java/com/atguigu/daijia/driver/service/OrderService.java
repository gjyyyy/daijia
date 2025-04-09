package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;

import java.util.List;

public interface OrderService {


    //查询订单状态
    Integer getOrderStatus(Long orderId);

    List<NewOrderDataVo> findNewOrderQueueData(Long driverId);

    //司机抢单
    Boolean robNewOrder(Long driverId, Long orderId);

    //司机端查找当前订单
    CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId);

    //获取订单账单详细信息
    OrderInfoVo getOrderInfo(Long orderId, Long driverId);

    //计算最佳驾驶线路
    DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm);

    //司机到达代驾起始地点
    Boolean driverArriveStartLocation(Long orderId, Long driverId);

    //更新代驾车辆信息
    Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm);

    //开始代驾服务
    Boolean startDrive(StartDriveForm startDriveForm);

    //结束代驾
    Boolean endDrive(OrderFeeForm orderFeeForm);

    //获取司机订单分页列表
    PageVo findDriverOrderPage(Long driverId, Long page, Long limit);

    //司机发送账单信息
    Boolean sendOrderBillInfo(Long orderId, Long driverId);
}
