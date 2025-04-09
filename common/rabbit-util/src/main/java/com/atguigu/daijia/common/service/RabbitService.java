package com.atguigu.daijia.common.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RabbitService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    //发送消息
    public boolean sendMessage(String exchange,
                               String routingkey,
                               String message) {
        rabbitTemplate.convertAndSend(exchange,routingkey,message);
        log.info("消息已发送： "+message);
        return true;
    }

}
