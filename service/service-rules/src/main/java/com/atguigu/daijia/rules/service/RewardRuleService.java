package com.atguigu.daijia.rules.service;

import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;

public interface RewardRuleService {

    //计算订单奖励费用
    RewardRuleResponseVo calculateOrderRewardFee(RewardRuleRequestForm rewardRuleRequestForm);

}
