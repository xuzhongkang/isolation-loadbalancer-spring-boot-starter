/*
 * © 2022-2023  嘉环科技股份有限公司. 版权所有。
 *
 * This software is protected by copyright laws and international copyright agreements. Without permission, no one may copy, modify, distribute, sell, or otherwise use this software.
 * We reserve the right to pursue all legal liabilities for copyright infringement. For users who obtain this software through illegal means, we will take legal action to hold them accountable.
 * The trademarks and logos included in this software are the property of their respective owners. They may not be used for any unauthorized purposes.
 * If you need to use this software or any of its features, please contact us to obtain a license or purchase a legal authorization certificate.
 *
 * 本软件受版权法和国际版权协议的保护。未经许可，任何人不得复制、修改、分发、出售、以及其他方式使用本软件。
 * 我们保留一切追究侵犯版权的法律责任的权利。对于通过非法手段获取该软件的用户，我们将采取法律手段追究其法律责任。
 * 该软件中包含的商标和标识属于其各自所有者的财产。不得将其用于任何未经授权的目的。
 * 如果您需要使用本软件或其中的部分功能，请联系我们获取许可或购买合法的授权证书。
 */

package com.bestlink.configuration;

import com.bestlink.condition.EnvironmentCondition;
import com.bestlink.loadbalancer.RibbonIsolationLoadBalancer;
import com.bestlink.properties.IsolationProperties;
import com.netflix.loadbalancer.IRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义负载均衡配置类。
 * <p>
 * 适用于 Feign + Ribbon 模式。
 * 限制在开发和测试环境(可以通过配置项进行修改{@link IsolationProperties})下，开发人员启动的本地服务可以注册到 Nacos 中进行本地调试，并且不影响线上环境其他人的请求。
 * <p>
 * {@link RibbonIsolationLoadBalancer}
 *
 * @author xuzhongkang
 * @since 2023/9/15 17:04
 **/

@Conditional(EnvironmentCondition.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(IRule.class)
public class RibbonIsolationConfiguration {
    @Bean
    public IRule isolationLoadBalancer() {
        return new RibbonIsolationLoadBalancer();
    }
}
