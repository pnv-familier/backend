package com.familier.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class UserProfileService {

    private final WebClient webClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${CORE_SERVICE_URL:http://localhost:8081}")
    private String coreServiceUrl;

    @Value("${application.security.internal.secret:default_internal_secret}")
    private String internalSecret;

    private static final String CACHE_PREFIX = "user_profile:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public UserProfileService(WebClient.Builder webClientBuilder,
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<Map<String, Object>> getUserProfile(String email) {
        String cacheKey = CACHE_PREFIX + email;

        log.debug("Fetching user profile: {}", email);

        // Try cache first
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedJson -> {
                    try {
                        Map<String, Object> cached = objectMapper.readValue(cachedJson, Map.class);
                        log.debug("Cache hit for user profile: {}", email);
                        return Mono.just(cached);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached profile, fetching fresh", e);
                        return fetchFromCoreService(email, cacheKey);
                    }
                })
                .switchIfEmpty(fetchFromCoreService(email, cacheKey))
                .onErrorResume(e -> {
                    log.error("Error fetching user profile: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    private Mono<Map<String, Object>> fetchFromCoreService(String email, String cacheKey) {
        String url = UriComponentsBuilder.fromHttpUrl(coreServiceUrl + "/ai/user-profile")
                .queryParam("email", email)
                .toUriString();

        return webClient.get()
                .uri(url)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(profile -> {
                    log.debug("Fetched user profile from Core Service: {}", email);

                    // Cache the result
                    return cacheProfile(cacheKey, (Map<String, Object>) profile)
                            .thenReturn((Map<String, Object>) profile);
                })
                .onErrorResume(e -> {
                    Throwable throwable = (e instanceof Throwable) ? (Throwable) e : new Exception(e.toString());
                    log.error("Error calling Core Service for user profile: {}", throwable.getMessage(), throwable);
                    return Mono.<Map<String, Object>>empty();
                });
    }

    private Mono<Boolean> cacheProfile(String cacheKey, Map<String, Object> profile) {
        try {
            String json = objectMapper.writeValueAsString(profile);
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, CACHE_TTL)
                    .doOnSuccess(success -> log.debug("Cached user profile: {}", cacheKey))
                    .onErrorResume(e -> {
                        log.warn("Failed to cache user profile", e);
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize profile for caching", e);
            return Mono.just(false);
        }
    }
}
