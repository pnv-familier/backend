package com.familier.ai.service.provider;

import com.familier.grpc.UserProfileResponse;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Primary
@Slf4j
public class CachingUserProvider implements UserProvider {

    private final UserProvider delegate;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String CACHE_KEY_PREFIX = "user:profile:";

    public CachingUserProvider(@Qualifier("internalUserProvider") UserProvider delegate, 
                               @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<UserProfileResponse> getUserProfile(String email) {
        String key = CACHE_KEY_PREFIX + email;

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    log.info("Cache Hit for email: {}", email);
                    try {
                        UserProfileResponse.Builder builder = UserProfileResponse.newBuilder();
                        JsonFormat.parser().merge(json, builder);
                        return Mono.just(builder.build());
                    } catch (Exception e) {
                        log.error("Error parsing cached profile for email {}: {}", email, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Redis lookup failed, bypassing cache for {}: {}", email, e.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(
                    delegate.getUserProfile(email)
                        .flatMap(profile -> {
                            log.info("Cache Miss for email: {}. Fetching from provider.", email);
                            try {
                                String json = JsonFormat.printer().print(profile);
                                Duration ttl = "User".equalsIgnoreCase(profile.getFullName()) ? Duration.ofMinutes(1) : Duration.ofMinutes(10);
                                
                                return redisTemplate.opsForValue().set(key, json, ttl)
                                        .onErrorResume(e -> {
                                            log.error("Failed to save to Redis cache for {}: {}", email, e.getMessage());
                                            return Mono.just(true);
                                        })
                                        .thenReturn(profile);
                            } catch (Exception e) {
                                log.error("Error serializing profile for cache: {}", e.getMessage());
                                return Mono.just(profile);
                            }
                        })
                );
    }
}
