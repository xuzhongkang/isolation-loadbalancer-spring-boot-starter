# isolation-loadbalancer-spring-boot-starter
针对于微服务架构下，本地启动的服务实例需要注册到集成开发环境、集成测试环境进行代码调试，使用默认的负载均衡策略时，通常会因为 debug 而阻塞其他人的请求，或者自己的请求路由到其他服务实例上。
isolation-loadbalancer-spring-boot-starter 此项目针对上述问题，自定义负载均衡策略，对本地流量和线上流量进行隔离，线上请求不会路由到本地启动的服务实例，本地请求会优先匹配本地启动的服务实例。
# 使用说明
pom.xml 中引入依赖(需要自行拉取代码构建)
```xml
        <dependency>
            <groupId>com.bestlink</groupId>
            <artifactId>isolation-loadbalancer-spring-boot-starter</artifactId>
            <version>1.2.2</version>
        </dependency>
```
在需要调试的本地服务的 application.yaml 中增加如下配置：
```yaml
local:
  isolation-loadbalancer:
    enabled: true
```
# 技术组件
+ Spring Cloud Gateway 3.1.3
+ SpringBoot 2.3.9
+ Spring Cloud Loadbalancer 3.1.3
+ Nacos 1.4.1
+ OpenFeign 2.2.9
+ Ribbon 2.3.0
# 适用场景
## 网关 -> 服务
在 spring cloud gateway 3.1.3 版本中测试通过。此版本 gateway 使用 spring cloud loadbalancer 进行负载均衡，默认情况下使用 RoundRobinLoadBalancer 进行负载均衡：
```java
public class LoadBalancerClientConfiguration {

    private static final int REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER = 193827465;

    @Bean
    @ConditionalOnMissingBean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(Environment environment,
                                                                                   LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RoundRobinLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
```
使用 ReactorIsolationRobinLoadBalancer 替换默认的 RoundRobinLoadBalancer，重写 choose 方法，流量图示如下：
<img width="1206" alt="image" src="https://github.com/xuzhongkang/isolation-loadbalancer-spring-boot-starter/assets/43947563/417497e6-5c94-4f45-80e8-91537a872315">
## 服务 -> 服务
在 Nacos + OpenFeign + Ribbon 下测试通过。Springboot 版本为 2.3.9，后续版本中默认的负载均衡组件不再使用 Ribbon ，需要自行测试。
默认情况下，Ribbon 使用 RoundRobinRule，即轮询的方式。使用 RibbonIsolationLoadBalancer 替换默认的 RoundRobinRule：
```java
public class RibbonIsolationConfiguration {
    @Bean
    public IRule isolationLoadBalancer() {
        return new RibbonIsolationLoadBalancer();
    }
}
```
流量图示如下：

<img width="1167" alt="image" src="https://github.com/xuzhongkang/isolation-loadbalancer-spring-boot-starter/assets/43947563/52ddfb29-c378-4a26-9941-d89203da9bd4">


