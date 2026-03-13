package com.familier.ai.service;

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
public class RelationMappingService {

    private final WebClient webClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${CORE_SERVICE_URL:http://localhost:8081}")
    private String coreServiceUrl;

    @Value("${application.security.internal.secret:default_internal_secret}")
    private String internalSecret;

    private static final String CACHE_PREFIX = "relation_mapping:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public RelationMappingService(WebClient.Builder webClientBuilder,
                                  @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
    }

    public Mono<String> mapRelationToEmail(String currentUserEmail, String relationType) {
        String cacheKey = CACHE_PREFIX + currentUserEmail + ":" + relationType;
        
        log.debug("Mapping relation to email: user={}, relation={}", currentUserEmail, relationType);

        // Try cache first
        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(cached -> log.debug("Cache hit for relation mapping: {}", cacheKey))
                .switchIfEmpty(fetchFromCoreService(currentUserEmail, relationType, cacheKey))
                .onErrorResume(e -> {
                    log.error("Error mapping relation to email", e);
                    return Mono.just("");
                });
    }

    private Mono<String> fetchFromCoreService(String currentUserEmail, String relationType, String cacheKey) {
        String url = UriComponentsBuilder.fromHttpUrl(coreServiceUrl + "/ai/map-relation-to-email")
                .queryParam("currentUserEmail", currentUserEmail)
                .queryParam("relationType", relationType)
                .toUriString();
        
        return webClient.get()
                .uri(url)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("targetEmail"))
                .flatMap(targetEmail -> {
                    log.debug("Mapped {} for user {} to email {}", relationType, currentUserEmail, targetEmail);
                    
                    // Cache the result
                    return redisTemplate.opsForValue()
                            .set(cacheKey, targetEmail, CACHE_TTL)
                            .thenReturn(targetEmail);
                })
                .onErrorResume(e -> {
                    log.error("Error calling Core Service for relation mapping: {}", e.getMessage(), e);
                    return Mono.just("");
                });
    }
}
