package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.client.CiFeignClient;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import com.atguigu.daijia.order.client.OrderMonitorFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MonitorServiceImpl implements MonitorService {

    @Autowired
    private OrderMonitorFeignClient orderMonitorFeignClient;

    @Autowired
    private FileService fileService;

    @Autowired
    private CiFeignClient ciFeignClient;

    //上传录音
    @Override
    public Boolean upload(MultipartFile file, OrderMonitorForm orderMonitorForm) {
        //上传录音文件到minio
        String url = fileService.upload(file);

        //上传录音文本到mongodb
        OrderMonitorRecord orderMonitorRecord = new OrderMonitorRecord();
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        orderMonitorRecord.setFileUrl(url);

        //增加文本审核
//        TextAuditingVo textAuditingVo =
//                ciFeignClient.textAuditing(orderMonitorForm.getContent()).getData();
        //方便起见直接返回文本审核结果为通过
        TextAuditingVo textAuditingVo = new TextAuditingVo();
        textAuditingVo.setResult("0");
        textAuditingVo.setKeywords("");

        orderMonitorRecord.setResult(textAuditingVo.getResult());
        orderMonitorRecord.setKeywords(textAuditingVo.getKeywords());

        orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord);

        return true;
    }
}
