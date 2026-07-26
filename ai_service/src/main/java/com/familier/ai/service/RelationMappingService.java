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

        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(cached -> log.debug("Cache hit for relation mapping: {}", cacheKey))
                .switchIfEmpty(fetchFromCoreService(currentUserEmail, relationType, cacheKey, true))
                .onErrorResume(e -> {
                    log.warn(
                            "Redis unavailable for relation mapping cache, bypassing cache for user={} relationType={}: {}",
                            currentUserEmail, relationType, e.getMessage());
                    return fetchFromCoreService(currentUserEmail, relationType, cacheKey, false);
                });
    }

    private Mono<String> fetchFromCoreService(String currentUserEmail, String relationType, String cacheKey,
            boolean cacheResult) {
        String url = UriComponentsBuilder.fromHttpUrl(coreServiceUrl + "/ai/map-relation-to-email")
                .queryParam("currentUserEmail", currentUserEmail)
                .queryParam("relationType", relationType)
                .toUriString();

        return webClient.get()
                .uri(url)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response -> {
                            log.debug("No relation mapping found for user={} relationType={}", currentUserEmail,
                                    relationType);
                            return Mono.error(new NoSuchFieldException("not_found"));
                        })
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("targetEmail"))
                .flatMap(targetEmail -> {
                    if (targetEmail == null || targetEmail.isEmpty()) {
                        return Mono.empty();
                    }
                    if (!cacheResult) {
                        return Mono.just(targetEmail);
                    }
                    return redisTemplate.opsForValue()
                            .set(cacheKey, targetEmail, CACHE_TTL)
                            .onErrorResume(cacheError -> {
                                log.warn("Failed to cache relation mapping for user={} relationType={}: {}",
                                        currentUserEmail, relationType, cacheError.getMessage());
                                return Mono.just(false);
                            })
                            .thenReturn(targetEmail);
                })
                .onErrorResume(e -> {
                    if (e instanceof NoSuchFieldException && "not_found".equals(e.getMessage())) {
                        return Mono.empty();
                    }
                    log.error("Error calling Core Service for relation mapping: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
