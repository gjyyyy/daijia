package com.atguigu.daijia.dispatch.xxl.jobtest;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class DispatchJobHandler {

    @XxlJob("firstJobHandler")
    public void testJobHandler() {
        System.out.println("xxl-job项目集成测试");
    }
}
