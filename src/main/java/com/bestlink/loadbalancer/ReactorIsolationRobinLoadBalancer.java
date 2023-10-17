package com.bestlink.loadbalancer;

import com.bestlink.configuration.LocalNacosServerInstanceConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.SelectedInstanceCallback;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 自定义负载均衡规则，通过 ip 和 metadata 选择服务实例，线上服务流量不会路由到本地服务，本地调试请求可以路由到本地服务，实现流量隔离。
 * 本地启动的服务中，会在 metadata 中携带 "local-instance-id" 标识，表明此服务实例来自本地。
 * 对于某次请求，首先尝试寻找与请求 ip 相同的服务实例（只会匹配元数据中有 "local-instance-id" 的服务实例，防止线上服务负载均衡失效），
 * 如果没有匹配对应的服务实例，在剩下的非本地服务中随机选择一个。
 * <p>
 * 用于 Spring Cloud Loadbalancer + Nacos 模式。
 *
 * @author xuzhongkang
 * @see ReactorServiceInstanceLoadBalancer
 * @since 2023/9/17 09:13
 **/
@Slf4j
@SuppressWarnings("rawtypes")
public class ReactorIsolationRobinLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    /**
     * @see com.bestlink.configuration.LocalNacosServerInstanceConfiguration#NACOS_METADATA_LOCAL_KEY
     */
    private static final String NACOS_METADATA_LOCAL_KEY = LocalNacosServerInstanceConfiguration.NACOS_METADATA_LOCAL_KEY;

    private static final String X_REAL_IP = "x-real-ip";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    private static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
    private static final String X_CLIENT_IP = "X_CLIENT_IP";
    private static final String UNKNOWN = "unknown";
    private static final String IP_SEPARATOR = ",";

    private static final Random RANDOM = new Random();
    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public ReactorIsolationRobinLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider, String serviceId) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }


    /**
     * see original
     * <a href="https://github.com/Netflix/ocelli/blob/master/ocelli-core/"> RoundRobinLoadBalancer </a>
     * src/main/java/netflix/ocelli/loadbalancer/RoundRobinLoadBalancer.java
     */
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next().map(serviceInstances -> processInstanceResponse(request, supplier, serviceInstances));
    }

    private Response<ServiceInstance> processInstanceResponse(Request request, ServiceInstanceListSupplier supplier, List<ServiceInstance> serviceInstances) {
        Response<ServiceInstance> serviceInstanceResponse = getInstanceResponse(request, serviceInstances);
        if (supplier instanceof SelectedInstanceCallback && serviceInstanceResponse.hasServer()) {
            ((SelectedInstanceCallback) supplier).selectedServiceInstance(serviceInstanceResponse.getServer());
        }
        return serviceInstanceResponse;
    }

    private Response<ServiceInstance> getInstanceResponse(Request request, List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn("No servers available for service: " + serviceId);
            }
            return new EmptyResponse();
        }

        ServiceInstance instance = null;
        try {
            // 尝试寻找本地启动的服务实例
            instance = tryMatchLocalInstance(request, instances);
            if (instance != null) {
                log.info("choose a local server instance [{}]", instance.getInstanceId());
            } else {
                // 从非本地启动的服务实例中，随机选择一个
                instance = randomOneWithoutLocalInstance(instances);
                if (instance != null) {
                    log.info("choose a random server instance [{}]", instance.getInstanceId());
                }
            }
        } catch (Exception e) {
            log.error("failed to match local server instance,cause:{}", e.getMessage());
        }
        return new DefaultResponse(instance);
    }

    /**
     * 对传入对 instances 集合进行过滤，去除本地服务（即 metadata 中携带 "local-instance-id"），在剩余服务中随机返回一个。
     * 如果过滤之后集合为空，返回 null。
     *
     * @param instances 服务实例集合
     * @return ServiceInstance，return null when filtered-collection is empty。
     */
    private ServiceInstance randomOneWithoutLocalInstance(List<ServiceInstance> instances) {
        List<ServiceInstance> list = instances.stream()
                .filter(instance -> !instance.getMetadata().containsKey(NACOS_METADATA_LOCAL_KEY))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(RANDOM.nextInt(list.size()));
    }

    /**
     * 根据 request 尝试寻找一个本地服务实例。
     * 在服务列表中找到本地启动的服务实例（即 metadata 中携带 "local-instance-id"），并且服务实例的 ip 与请求 ip 相同。
     * 如果没有找到，返回 null。
     *
     * @param instances 服务实例列表。
     * @param request   请求对象，通过解析 request 获取请求方的原始 ip。
     * @return ServiceInstance，return null when filtered-collection is empty。
     */
    private ServiceInstance tryMatchLocalInstance(Request request, List<ServiceInstance> instances) {
        RequestDataContext context = (RequestDataContext) request.getContext();
        RequestData requestData = context.getClientRequest();
        if (requestData == null) {
            return null;
        }
        String originIp = getOriginIp(requestData);
        for (ServiceInstance instance : instances) {
            // 当前服务实例元数据中携带 "local-instance-id" 并且 host 与原始请求 ip 一致时，认为是本地启动的服务实例。
            if (instance.getMetadata().containsKey(NACOS_METADATA_LOCAL_KEY) && instance.getHost().equals(originIp)) {
                return instance;
            }
        }
        return null;
    }

    /**
     * 获取原始请求 ip，获取的值依赖于 nginx/gateway 的配置。
     *
     * @param request request
     */
    private String getOriginIp(RequestData request) {
        String ip = request.getHeaders().getFirst(X_REAL_IP);
        if (notFound(ip)) {
            ip = request.getHeaders().getFirst(X_FORWARDED_FOR);
        }
        if (notFound(ip)) {
            ip = request.getHeaders().getFirst(PROXY_CLIENT_IP);
        }
        if (notFound(ip)) {
            ip = request.getHeaders().getFirst(WL_PROXY_CLIENT_IP);
        }
        if (notFound(ip)) {
//            log.warn("can not get origin ip from {},{},{},{},the most possible cause is had not set Nginx config [proxy_set_header] ",
//                    X_FORWARDED_FOR, X_REAL_IP, PROXY_CLIENT_IP, WL_PROXY_CLIENT_IP);
            // 依赖全局过滤器中添加的调用者 ip
            ip = request.getHeaders().getFirst(X_CLIENT_IP);
        }
        // 处理多IP的情况只取第一个IP
        if (StringUtils.hasLength(ip) && ip.contains(IP_SEPARATOR)) {
            String[] ipArray = ip.split(IP_SEPARATOR);
            ip = ipArray[0];
        }
        log.info("found an origin ip:{}", ip);
        return ip;
    }

    private boolean notFound(String ip) {
        return !StringUtils.hasLength(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }
}