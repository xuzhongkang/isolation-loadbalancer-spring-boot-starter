package com.bestlink.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 流量隔离属性配置
 *
 * @author xuzhongkang
 * @since 2023/9/20 19:54
 **/

@Data
@Configuration
@ConfigurationProperties(prefix = "local.isolation-loadbalancer")
public class IsolationProperties {

    /**
     * 服务之间定向 ip 调用。
     */
    private String targetIp = "127.0.0.1";

    /**
     * 启动本地服务调试，默认 false。
     */
    private Boolean enabled = false;

    /**
     * 本地服务的标识
     */
    private String localInstanceId = "local-instance";

    /**
     * 限制在哪个环境下生效，默认开发和测试环境。
     */
    private String[] activeEnv = {"dev", "test"};

}
