package com.example.gateway;

import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(a -> a.getAddress().getHostAddress())
                        .orElse("unknown"));
    }

    // Ví dụ theo người dùng (sau khi đã xác thực):
    // @Bean
    // KeyResolver userKeyResolver() {
    //     return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-User-Id"));
    // }
}
