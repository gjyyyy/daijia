package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.login.CheckLogin;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.atguigu.daijia.common.result.Result;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerController {
    @Autowired
    private CustomerService customerService;

    @Operation(summary = "更新用户微信手机号")
    @CheckLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
        //return Result.ok(customerInfoService.updateWxPhoneNumber(updateWxPhoneForm));

        //非企业微信直接返回true用来测试
        return Result.ok(true);
    }

    @Operation(summary = "小程序登录")
    @GetMapping("/login/{code}")
    public Result<String> wxLogin(@PathVariable String code){
        return Result.ok(customerService.login(code));
    }

    @CheckLogin
    @Operation(summary = "获取客户登录信息")
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerLoginInfo(){
        Long customerId = AuthContextHolder.getUserId();
        CustomerLoginVo customerLoginVo = customerService.getCustomerLoginInfo(customerId);
        return Result.ok(customerLoginVo);
    }

//    @Operation(summary = "获取客户登录信息")
//    @GetMapping("/getCustomerLoginInfo")
//    public Result<CustomerLoginVo> getCustomerLoginInfo(@RequestHeader(value = "token") String token){
//        CustomerLoginVo customerLoginVo = customerService.getCustomerLoginInfo(token);
//        return Result.ok(customerLoginVo);
//    }
}

