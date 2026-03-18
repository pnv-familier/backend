package com.familier.ai.service.provider;

import com.familier.grpc.UserProfileResponse;
import reactor.core.publisher.Mono;

public interface UserProvider {
    Mono<UserProfileResponse> getUserProfile(String email);
}
