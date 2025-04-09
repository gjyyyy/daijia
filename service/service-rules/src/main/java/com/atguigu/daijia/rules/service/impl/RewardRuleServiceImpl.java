package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.RewardRuleRequest;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponse;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.rules.mapper.RewardRuleMapper;
import com.atguigu.daijia.rules.service.RewardRuleService;
import com.atguigu.daijia.utils.DroolsHelper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class RewardRuleServiceImpl implements RewardRuleService {

    private static final String RULES_CUSTOMER_RULES_DRL = "rules/RewardRule.drl";

    //计算订单奖励费用
    @Override
    public RewardRuleResponseVo calculateOrderRewardFee(RewardRuleRequestForm rewardRuleRequestForm) {

        RewardRuleRequest rewardRuleRequest = new RewardRuleRequest();
        rewardRuleRequest.setOrderNum(rewardRuleRequestForm.getOrderNum());
        rewardRuleRequest.setStartTime(rewardRuleRequestForm.getStartTime().toString());

        KieSession kieSession = DroolsHelper.loadForRule(RULES_CUSTOMER_RULES_DRL);

        RewardRuleResponse rewardRuleResponse = new RewardRuleResponse();
        kieSession.setGlobal("rewardRuleResponse",rewardRuleResponse);

        kieSession.insert(rewardRuleRequest);
        kieSession.fireAllRules();

        kieSession.dispose();

        //封装RewardRuleResponseVo
        RewardRuleResponseVo rewardRuleResponseVo = new RewardRuleResponseVo();
        rewardRuleResponseVo.setRewardAmount(rewardRuleResponse.getRewardAmount());
        return rewardRuleResponseVo;
    }
}
