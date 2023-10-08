package com.bestlink.configuration;

import com.bestlink.condition.EnvironmentCondition;
import com.bestlink.filter.GlobalClientIpFilter;
import com.bestlink.loadbalancer.ReactorIsolationRobinLoadBalancer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * spring cloud loadbalancer 配置类，注入自定义的负载均衡实现类。适用于 spring cloud gateway + spring cloud loadbalancer + nacos 模式。
 * <p>
 * 在本地服务注册到线上环境的注册中心时，通过 {@link ReactorIsolationRobinLoadBalancer } 进行流量隔离。
 *
 * @author xuzhongkang
 * @see <a href="https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer">
 * spring-cloud-loadbalancer
 * </a>
 * @since 2023/9/8 08:34
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@AutoConfigureAfter(LoadBalancerClientConfiguration.class)
@ConditionalOnMissingClass("com.netflix.loadbalancer.IRule")
@LoadBalancerClients(defaultConfiguration = ReactorIsolationLoadBalancerConfiguration.class)
public class ReactorIsolationLoadBalancerConfiguration {

    @Bean
    @Conditional(EnvironmentCondition.class)
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(Environment environment, LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new ReactorIsolationRobinLoadBalancer(loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }

    @Bean
    @ConditionalOnBean(GlobalFilter.class)
    @Conditional(EnvironmentCondition.class)
    public GlobalClientIpFilter globalClientIpFilter() {
        return new GlobalClientIpFilter();
    }
}
