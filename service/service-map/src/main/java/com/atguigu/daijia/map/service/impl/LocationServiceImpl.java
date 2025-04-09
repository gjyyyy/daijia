package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.repository.OrderServiceLocationRepository;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import io.lettuce.core.GeoArgs;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    private OrderServiceLocationRepository orderServiceLocationRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        //把司机位置信息添加redis里面geo
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(),
                updateDriverLocationForm.getLatitude().doubleValue());
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION,point,updateDriverLocationForm.getDriverId().toString());
        return true;
    }

    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION,driverId.toString());
        return true;
    }

    //搜索附近满足条件的司机
    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        //搜索经纬度位置5公里以内的司机
        //1 操作redis里面geo
        //创建point，经纬度位置
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue()
                                ,searchNearByDriverForm.getLatitude().doubleValue());
        //定义距离，5公里
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS,
                                            RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle = new Circle(point,distance);
        //定义GEO参数，设置返回结果包含内容
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeCoordinates()
                                .includeDistance()
                                        .sortAscending();
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);

        //2 查询redis最终返回list集合
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        //3 对查询list集合进行处理
        // 遍历list集合，得到每个司机信息
        // 根据每个司机个性化设置信息判断
        List<NearByDriverVo> list = new ArrayList<>();
        if(!CollectionUtils.isEmpty(content)){
            Iterator<GeoResult<RedisGeoCommands.GeoLocation<String>>> iterator = content.iterator();
            while(iterator.hasNext()){
                GeoResult<RedisGeoCommands.GeoLocation<String>> item = iterator.next();

                //远程调用，根据司机id个性化设置信息
                Long driverId = Long.parseLong(item.getContent().getName());
                Result<DriverSet> driverSetResult = driverInfoFeignClient.getDriverSet(driverId);
                DriverSet driverSet = driverSetResult.getData();

                //判断订单里程order_distance
                BigDecimal orderDistance = driverSet.getOrderDistance();
                //orderDistance==0，司机没有限制的
                if(orderDistance.doubleValue() != 0
                        && orderDistance.subtract(searchNearByDriverForm.getMileageDistance()).doubleValue()<0) {
                    continue;
                }

                //判断接单里程 accept_distance
                //当前接单距离
                BigDecimal currentDistance =
                        new BigDecimal(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);

                BigDecimal acceptDistance = driverSet.getAcceptDistance();
                if(acceptDistance.doubleValue() !=0
                        && acceptDistance.subtract(currentDistance).doubleValue()<0) {
                    continue;
                }

                //封装符合条件的数据
                NearByDriverVo nearByDriverVo = new NearByDriverVo();
                nearByDriverVo.setDriverId(driverId);
                nearByDriverVo.setDistance(currentDistance);
                list.add(nearByDriverVo);
            }
        }

        return list;
    }

    //司机赶往代驾起始点：更新订单地址到缓存
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());

        String key = RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId();

        redisTemplate.opsForValue().set(key,orderLocationVo);
        return true;
    }

    //司机赶往代驾起始点：获取订单经纬度位置
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        String key = RedisConstant.UPDATE_ORDER_LOCATION + orderId;
        return (OrderLocationVo) redisTemplate.opsForValue().get(key);
    }

    //批量保存代驾服务订单位置
    @Override
    public Boolean
        saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> list = new ArrayList<>();
        orderLocationServiceFormList.forEach(orderLocationServiceForm->{
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(orderLocationServiceForm,orderServiceLocation);
            orderServiceLocation.setId(ObjectId.get().toString());
            orderServiceLocation.setCreateTime(new Date());

            list.add(orderServiceLocation);
        });

        //批量添加到MongoDB
        orderServiceLocationRepository.saveAll(list);
        return true;
    }

    //代驾服务：获取订单服务最后一个位置信息
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);

        OrderServiceLocation orderServiceLocation =
                mongoTemplate.findOne(query, OrderServiceLocation.class);
        OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
        BeanUtils.copyProperties(orderServiceLocation,orderServiceLastLocationVo);

        return orderServiceLastLocationVo;
    }

    //代驾服务：计算订单实际里程
    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {

        //MongoRepository只需要 按照规则 在MongoRepository把查询方法创建出来就可以了
        // 总体规则：
        //1 查询方法名称 以 get  |  find  | read开头
        //2 后面查询字段名称，满足驼峰式命名，比如OrderId
        //3 字段查询条件添加关键字，比如Like  OrderBy   Asc
        // 具体编写 ： 根据订单id获取代驾订单位置信息，根据创建时间排序（升序）
        List<OrderServiceLocation> list =
                orderServiceLocationRepository.findByOrderIdOrderByCreateTimeAsc(orderId);

        double realDistance = 0;
        if(!CollectionUtils.isEmpty(list)){
            for(int i=0;i<list.size()-1;i++){
                OrderServiceLocation location1 = list.get(i);
                OrderServiceLocation location2 = list.get(i + 1);
                double distance = LocationUtil.getDistance(location1.getLatitude().doubleValue(),
                        location1.getLongitude().doubleValue(),
                        location2.getLatitude().doubleValue(),
                        location2.getLongitude().doubleValue());
                realDistance += distance;
            }
        }
        return new BigDecimal(realDistance);
    }
}
