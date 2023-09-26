package com.bestlink.loadbalancer;

import com.alibaba.cloud.nacos.ribbon.NacosServer;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 自定义负载均衡类，用于多个服务实例注册到一个 nacos 空间下时，不同请求选择不同的服务实例，实现流量隔离。
 * 用于 Feign + Ribbon + Nacos 模式。
 * <p>
 * {@link  com.netflix.loadbalancer.AbstractLoadBalancerRule }
 * {@link com.netflix.loadbalancer.IRule}
 *
 * @author xuzhongkang
 * @since 2023/9/15 19:15
 **/
@Slf4j
public class RibbonIsolationLoadBalancer extends AbstractLoadBalancerRule {

    private static final String NACOS_METADATA_LOCAL_KEY = "local-instance-id";
    public static final int RETRY_MAX = 10;

    private static final String X_REAL_IP = "x-real-ip";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    private static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
    private static final String X_CLIENT_IP = "X_CLIENT_IP";
    private static final String UNKNOWN = "unknown";
    private static final String IP_SEPARATOR = ",";

    private final RoundRobinRule DEFAULT_RULE = new RoundRobinRule();
    private final Random random = new Random();

    @Value("${local.isolation-loadbalancer.target-ip:''}")
    private String targetIp;

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        String clientName = iClientConfig.getClientName();
        log.info("clientName::{}", clientName);
    }

    @Override
    public void setLoadBalancer(ILoadBalancer lb) {
        super.setLoadBalancer(lb);
        DEFAULT_RULE.setLoadBalancer(lb);
    }

    @Override
    public Server choose(Object key) {
        // 根据 request 选择服务实例，如果没有获取到 request，使用默认的负载均衡规则。
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return DEFAULT_RULE.choose(key);
        }
        HttpServletRequest request = requestAttributes.getRequest();
        Server server = null;
        try {
            server = tryMatchLocalInstance(request);
            if (server != null) {
                log.info("choose a local server instance:{}", server.getHost());
            }
        } catch (Exception e) {
            log.warn("failed to match local server instance,cause:{}", e.getMessage());
        }
        // 如果没找到匹配的 server，从非本地服务中随机选择一个
        return server != null ? server : randomChooseWithoutLocalServer();
    }

    private Server randomChooseWithoutLocalServer() {
        List<Server> allServers = this.getLoadBalancer().getAllServers();
        List<Server> serverList = allServers.stream().filter(notLocalServer()).collect(Collectors.toList());
        if (serverList.isEmpty()) {
            log.warn("No servers available from load balancer: {}", this.getLoadBalancer());
            return null;
        }
        if (serverList.size() == 1) {
            return serverList.get(0);
        }
        Server server = null;
        int count = 0;
        while (count++ < RETRY_MAX) {
            server = serverList.get(random.nextInt(serverList.size()));
            if (server.isAlive() && server.isReadyToServe()) {
                log.info("choose a random server instance [{}]", server.getHost());
                return server;
            }
            Thread.yield();
        }
        if (count >= RETRY_MAX) {
            log.warn("No available alive servers after 10 tries from load balancer: {} ", this.getLoadBalancer());
        }
        return server;
    }

    private Predicate<Server> notLocalServer() {
        return server -> {
            if (server instanceof NacosServer) {
                return !((NacosServer) server).getMetadata().containsKey(NACOS_METADATA_LOCAL_KEY);
            }
            return false;
        };
    }

    private Server tryMatchLocalInstance(HttpServletRequest request) {
        List<Server> allServers = this.getLoadBalancer().getAllServers();
        if (request == null || allServers.isEmpty()) {
            return null;
        }
        String originIp = getOriginIp(request);
        log.info("this request is from ip:{}", originIp);
        return allServers.stream().filter(server -> originIp.equals(server.getHost()))
                // 只匹配元数据中含有本地服务标识（"local-instance-id"）的实例，否则会造成线上服务负载均衡失效。
                .filter(server -> ((NacosServer) server).getMetadata().containsKey(NACOS_METADATA_LOCAL_KEY))
                .findFirst()
                .orElse(null);
    }


    /**
     * 判断是否是指定 ip 的服务, 用于多个本地服务之间的定向调用
     * 根据配置文件中配置的特定 ip，筛选与之匹配的服务。
     */
    //todo 暂不开放此功能
    private boolean specTargetIpMatch(NacosServer server) {
        if (!StringUtils.hasLength(targetIp)) {
            return false;
        }
        return targetIp.equals(server.getHost());
    }

    /**
     * 获取原始请求 ip，获取的值依赖于 nginx 和 gateway 的配置。
     */
    private String getOriginIp(HttpServletRequest request) {
        String ip = request.getHeader(X_REAL_IP);
        if (StringUtils.hasLength(ip)) {
            log.info("get origin ip[{}] from header:{}", ip, X_REAL_IP);
        }
        if (notFound(ip)) {
            ip = request.getHeader(X_FORWARDED_FOR);
            log.info("try to get origin ip[{}] from header:{}", ip, X_FORWARDED_FOR);
        }
        if (notFound(ip)) {
            ip = request.getHeader(PROXY_CLIENT_IP);
            log.info("try to get origin ip[{}] from header:{}", ip, PROXY_CLIENT_IP);
        }
        if (notFound(ip)) {
            ip = request.getHeader(WL_PROXY_CLIENT_IP);
            log.info("try to get origin ip[{}] from header:{}", ip, WL_PROXY_CLIENT_IP);
        }
        if (notFound(ip)) {
            // 获取网关中传递的 IP。
            ip = request.getHeader(X_CLIENT_IP);
            log.info("try to get origin ip[{}] from header:{}", ip, X_CLIENT_IP);
        }
        if (notFound(ip)) {
            log.warn("can not get origin ip from {},{},{},{},the most possible cause is had not set Nginx config [proxy_set_header] "
                    , X_FORWARDED_FOR, X_REAL_IP, PROXY_CLIENT_IP, WL_PROXY_CLIENT_IP);
            ip = request.getRemoteAddr();
        }
        // 处理多IP的情况只取第一个IP
        if (StringUtils.hasLength(ip) && ip.contains(IP_SEPARATOR)) {
            String[] ipArray = ip.split(IP_SEPARATOR);
            ip = ipArray[0];
        }
        return ip;
    }

    private boolean notFound(String ip) {
        return !StringUtils.hasLength(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }
}