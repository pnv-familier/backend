package com.familier.ai.service.provider;

import com.familier.grpc.UserProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

@Service("internalUserProvider")
@Profile("prod")
public class RestUserProvider implements UserProvider {

    private static final Logger logger = LoggerFactory.getLogger(RestUserProvider.class);
    private final WebClient webClient;
    private final String internalSecret;

    public RestUserProvider(WebClient.Builder webClientBuilder, 
                            @Value("${CORE_SERVICE_URL}") String coreServiceUrl,
                            @Value("${application.security.internal.secret}") String internalSecret) {
        this.internalSecret = internalSecret;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5));

        this.webClient = webClientBuilder
                .baseUrl(coreServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<UserProfileResponse> getUserProfile(String email) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ai/user-profile")
                        .queryParam("email", email)
                        .build())
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> UserProfileResponse.newBuilder()
                        .setEmail((String) map.getOrDefault("email", ""))
                        .setFullName((String) map.getOrDefault("fullName", ""))
                        .setProfileJson((String) map.getOrDefault("profileJson", "{}"))
                        .build())
                .retry(2)
                .onErrorResume(e -> {
                    logger.error("Failed to fetch user profile from Core Service for email: {}. Fallback to default. Error: {}", email, e.getMessage());
                    return Mono.just(UserProfileResponse.newBuilder()
                            .setEmail(email)
                            .setFullName("User")
                            .setProfileJson("{}")
                            .build());
                });
    }
}
