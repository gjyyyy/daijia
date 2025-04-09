package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DriverInfoService extends IService<DriverInfo> {

    Long login(String code);

    DriverLoginVo getDriverInfo(Long driverId);

    DriverAuthInfoVo getDriverAuthInfo(Long driverId);

    Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm);

    Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm);

    //获取司机个性化设置信息
    DriverSet getDriverSet(Long driverId);

    //当时是否进行人脸识别
    Boolean isFaceRecognition(Long driverId);

    //验证人脸识别
    Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm);

    //更新接单状态
    Boolean updateServiceStatus(Long driverId, Integer status);

    //获取司机基本信息
    DriverInfoVo getDriverInfoOrder(Long driverId);

    //获取司机OpenId
    String getDriverOpenId(Long driverId);
}
