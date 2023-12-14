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

package com.bestlink.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 直接访问网关时，记录客户端 ip。
 *
 * @author xuzhongkang
 * @since 2023/9/20 16:54
 **/
@Slf4j
public class GlobalClientIpFilter implements GlobalFilter, Ordered {

    private static final String CLIENT_IP = "X_CLIENT_IP";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String clientIp = remoteAddress == null ? "" : remoteAddress.getHostString();
        log.info("get client ip : [{}]", clientIp);
        ServerHttpRequest mutableReq = exchange.getRequest()
                .mutate()
                .header(CLIENT_IP, clientIp)
                .build();
        ServerWebExchange mutableExchange = exchange.mutate().request(mutableReq).build();
        return chain.filter(mutableExchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
