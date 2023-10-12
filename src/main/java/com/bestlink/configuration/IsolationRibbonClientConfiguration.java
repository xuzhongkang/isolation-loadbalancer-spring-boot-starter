package com.bestlink.configuration;

import com.bestlink.loadbalancer.RibbonIsolationRule;
import com.netflix.loadbalancer.IRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xuzhongkang
 * @since 2023/10/12 14:02
 **/
@Configuration(proxyBeanMethods = false)
public class IsolationRibbonClientConfiguration {

    @Bean
    public IRule ribbonRule() {
        return new RibbonIsolationRule();
    }
}
