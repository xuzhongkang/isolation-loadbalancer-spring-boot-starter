package com.bestlink.condition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.HashSet;
import java.util.Set;

/**
 * 控制自定义负载均衡能够使用的环境。
 * <p>
 * 默认为开发（dev）和测试（test）环境，可以通过 "local.isolation-loadbalancer.active-env" 进行配置。
 * 服务启动时会根据 "spring.profiles.active" 的值判断，如果 "spring.profiles.active" 和 "local.isolation-loadbalancer.active-env" 有交集则生效，否则不生效。
 * <p>
 *
 * @author xuzhongkang
 * @see com.bestlink.configuration.RibbonIsolationConfiguration
 * @see com.bestlink.configuration.ReactorIsolationLoadBalancerConfiguration
 * @since 2023/9/25 12:54
 **/
@SuppressWarnings("unchecked")
@Slf4j
public class EnvironmentCondition implements Condition {

    private static final String SPRING_PROFILES_ACTIVE = "spring.profiles.active";
    private static final String ISOLATION_ENV_ACTIVE = "local.isolation-loadbalancer.active-env";
    private static final Set<String> DEFAULT_ISOLATION_ENV_ACTIVE = new HashSet<>();


    public EnvironmentCondition() {
        DEFAULT_ISOLATION_ENV_ACTIVE.add("dev");
        DEFAULT_ISOLATION_ENV_ACTIVE.add("test");
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean matched = false;
        try {
            matched = envMatches(context);
        } catch (Exception e) {
            log.error("can not match active env:{}", e.getMessage());
        }
        return matched;
    }

    private boolean envMatches(ConditionContext context) {
        Environment environment = context.getEnvironment();
        Set<String> activeEnvSet = environment.getProperty(SPRING_PROFILES_ACTIVE, Set.class);
        Set<String> specEnvSet = environment.getProperty(ISOLATION_ENV_ACTIVE, Set.class);
        if (activeEnvSet == null) {
            return false;
        }
        if (specEnvSet == null) {
            specEnvSet = DEFAULT_ISOLATION_ENV_ACTIVE;
        }
        return specEnvSet.stream().anyMatch(activeEnvSet::contains);
    }
}
