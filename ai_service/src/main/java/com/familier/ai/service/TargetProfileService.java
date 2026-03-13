package com.familier.ai.service;

import com.familier.ai.dto.TargetProfileWithRelation;
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

@Service
@Slf4j
public class TargetProfileService {

    private final WebClient webClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${CORE_SERVICE_URL:http://localhost:8081}")
    private String coreServiceUrl;

    @Value("${application.security.internal.secret:default_internal_secret}")
    private String internalSecret;

    private static final String CACHE_PREFIX = "target_profile:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public TargetProfileService(WebClient.Builder webClientBuilder,
                                @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<TargetProfileWithRelation> getTargetProfile(String currentUserEmail, String targetUserEmail) {
        String cacheKey = CACHE_PREFIX + currentUserEmail + ":" + targetUserEmail;
        
        log.debug("Fetching target profile: current={}, target={}", currentUserEmail, targetUserEmail);

        // Try cache first
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedJson -> {
                    try {
                        TargetProfileWithRelation cached = objectMapper.readValue(cachedJson, TargetProfileWithRelation.class);
                        log.debug("Cache hit for target profile: {}", targetUserEmail);
                        return Mono.just(cached);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached profile, fetching fresh", e);
                        return fetchFromCoreService(currentUserEmail, targetUserEmail, cacheKey);
                    }
                })
                .switchIfEmpty(fetchFromCoreService(currentUserEmail, targetUserEmail, cacheKey))
                .onErrorResume(e -> {
                    log.error("Error fetching target profile", e);
                    return Mono.just(TargetProfileWithRelation.builder().build());
                });
    }

    private Mono<TargetProfileWithRelation> fetchFromCoreService(String currentUserEmail, String targetUserEmail, String cacheKey) {
        String url = UriComponentsBuilder.fromHttpUrl(coreServiceUrl + "/ai/user-profile-with-relation")
                .queryParam("currentUserEmail", currentUserEmail)
                .queryParam("targetUserEmail", targetUserEmail)
                .toUriString();
        
        return webClient.get()
                .uri(url)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .bodyToMono(TargetProfileWithRelation.class)
                .flatMap(profile -> {
                    log.debug("Fetched target profile from Core Service: {}", targetUserEmail);
                    
                    // Cache the result
                    return cacheProfile(cacheKey, profile)
                            .thenReturn(profile);
                })
                .onErrorResume(e -> {
                    log.error("Error calling Core Service for target profile: {}", e.getMessage(), e);
                    return Mono.just(TargetProfileWithRelation.builder().build());
                });
    }

    private Mono<Boolean> cacheProfile(String cacheKey, TargetProfileWithRelation profile) {
        try {
            String json = objectMapper.writeValueAsString(profile);
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, CACHE_TTL)
                    .doOnSuccess(success -> log.debug("Cached target profile: {}", cacheKey))
                    .onErrorResume(e -> {
                        log.warn("Failed to cache target profile", e);
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize profile for caching", e);
            return Mono.just(false);
        }
    }
}
