package com.atguigu.daijia.order.handler;

import com.atguigu.daijia.order.service.OrderInfoService;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RedisDelayHandle {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderInfoService orderInfoService;

    @PostConstruct
    public void listener() {
        new Thread(()->{
            while(true) {
                RBlockingQueue<String> queue_cancel =
                        redissonClient.getBlockingQueue("queue_cancel");
                try {
                    String orderId = queue_cancel.take();
                    //取消订单
                    if (StringUtils.hasText(orderId)) {
                        //调用方法取消订单
                        orderInfoService.orderCancel(Long.parseLong(orderId));
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
