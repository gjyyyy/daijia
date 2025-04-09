package com.atguigu.daijia.common.login;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.RequestContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect //切面类
public class CheckLoginAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    //环绕通知，登录判断
    //切入点表达式：指定对哪些规则的方法进行增强
    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(checkLogin)")
    public Object login(ProceedingJoinPoint proceedingJoinPoint,CheckLogin checkLogin) throws Throwable {

        //获取request
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) attributes;
        HttpServletRequest request = requestAttributes.getRequest();

        //获得token
        String token = request.getHeader("token");

        //判断token是否为空，为空，返回登录异常
        if(!StringUtils.hasText(token)){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }

        //token不为空，查看redis
        String customerId = (String) redisTemplate.opsForValue()
                .get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);

        //查询redis对应用户id，把用户id放到ThreadLocal里面
        if(!StringUtils.hasText(customerId)){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }
        AuthContextHolder.setUserId(Long.parseLong(customerId));

        //执行业务流程
        return proceedingJoinPoint.proceed();
    }
}
