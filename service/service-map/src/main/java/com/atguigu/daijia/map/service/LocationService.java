package com.atguigu.daijia.map.service;

import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;

import java.math.BigDecimal;
import java.util.List;

public interface LocationService {

    //更新司机位置
    Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm);

    //删除司机位置信息
    Boolean removeDriverLocation(Long driverId);

    //搜索附近满足条件的司机
    List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm);

    //司机赶往代驾起始点：更新订单地址到缓存
    Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm);

    //司机赶往代驾起始点：获取订单经纬度位置
    OrderLocationVo getCacheOrderLocation(Long orderId);

    //批量保存代驾服务订单位置
    Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList);

    //代驾服务：获取订单服务最后一个位置信息
    OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId);

    //代驾服务：计算订单实际里程
    BigDecimal calculateOrderRealDistance(Long orderId);
}
