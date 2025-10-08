package com.charagol.shortlink.project.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始化限流配置
 * 这个类在Spring应用启动后，会加载Sentinel的限流规则（继承自 InitializingBean ）。
 */
@Component
public class SentinelRuleConfig implements InitializingBean {

    /**
     * 当Spring容器初始化完这个Bean的所有属性后，会调用此方法。
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 1. 创建一个规则列表
        List<FlowRule> rules = new ArrayList<>();

        // 2. 定义针对 "create_short-link" 资源的流控规则
        FlowRule createOrderRule = new FlowRule();
        createOrderRule.setResource("create_short-link");        // 设定资源名称。 Sentinel会根据这个名称来查找并应用对应的规则
        createOrderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);   // 选择基于 QPS 进行限流
        createOrderRule.setCount(1);                             // 设定限流阈值

        // 3. 将规则添加到规则列表
        rules.add(createOrderRule);

        // 4. 将定义好的规则加载到 Sentinel
        FlowRuleManager.loadRules(rules);
    }
    /** NOTE
     *     使用方法：在需要限流的方法上添加注解：
     *         @SentinelResource(
     *                 value = "create_short-link",
     *                 blockHandler = "createShortLinkBlockHandlerMethod",
     *                 blockHandlerClass = CustomBlockHandler.class
     *         )
     *     1. value 值必须与 setResource 中的资源名称一致
     *     2. 第二点和第三点指定哪个类中的哪个兜底方法。
     *      2.1 blockHandler 值必须与 setBlockHandler 中兜底的方法名称一致
     *      2.2 blockHandlerClass 值必须与 setBlockHandler 中兜底的方法所在的类一致
     */
}