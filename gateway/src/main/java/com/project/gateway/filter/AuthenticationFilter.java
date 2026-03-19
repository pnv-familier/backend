package com.project.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {


    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .filter(c -> c.getAuthentication() != null && c.getAuthentication().isAuthenticated())
                .map(c -> c.getAuthentication())
                .flatMap(auth -> {
                    String username = auth.getPrincipal().toString();
                    String role = auth.getAuthorities().stream()
                            .findFirst()
                            .map(a -> a.getAuthority())
                            .orElse("");
                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .header("X-User-Email", username)
                            .header("X-User-Role", role)
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    public static class Config {
    }
}
