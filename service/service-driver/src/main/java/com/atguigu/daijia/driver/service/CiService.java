package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.order.TextAuditingVo;

public interface CiService {

    Boolean imageAuditing(String path);

    //文本审核
    TextAuditingVo textAuditing(String content);
}
