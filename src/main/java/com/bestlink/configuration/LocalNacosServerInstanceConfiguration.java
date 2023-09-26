package com.bestlink.configuration;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;
import com.alibaba.cloud.nacos.discovery.NacosWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地启动的服务实例，向 Nacos 注册时，增加 local-instance-id 标识。
 * <p>
 * 通过 local.isolation-loadbalancer.enabled 开启，默认为 false。
 *
 * @author xuzhongkang
 * @since 2023/9/21 11:45
 **/
@Configuration
@ConditionalOnProperty(value = "local.isolation-loadbalancer.enabled", havingValue = "true")
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
@AutoConfigureBefore({SimpleDiscoveryClientAutoConfiguration.class,
        CommonsClientAutoConfiguration.class})
@AutoConfigureAfter(NacosDiscoveryAutoConfiguration.class)
public class LocalNacosServerInstanceConfiguration {


    private static final String NACOS_METADATA_LOCAL_KEY = "local-instance-id";

    @Value("${local.isolation-loadbalancer.local-instance-id:local-instance}")
    private String localInstanceId;

    @Bean
    @ConditionalOnProperty(value = "spring.cloud.nacos.discovery.watch.enabled", matchIfMissing = true)
    public NacosWatch localNacosWatch(NacosServiceManager nacosServiceManager,
                                      NacosDiscoveryProperties nacosDiscoveryProperties) {
        Map<String, String> metadata = nacosDiscoveryProperties.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>(1);
        }
        metadata.put(NACOS_METADATA_LOCAL_KEY, localInstanceId);
        return new NacosWatch(nacosServiceManager, nacosDiscoveryProperties);
    }
}
