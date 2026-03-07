package com.familier.ai.service.provider;

import com.familier.grpc.UserContextServiceGrpc;
import com.familier.grpc.UserProfileResponse;
import com.familier.grpc.UserRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service("internalUserProvider")
@Profile("!prod")
public class GrpcUserProvider implements UserProvider {

    private static final Logger logger = LoggerFactory.getLogger(GrpcUserProvider.class);

    @GrpcClient("usercontext")
    private UserContextServiceGrpc.UserContextServiceBlockingStub userStub;

    @Override
    public Mono<UserProfileResponse> getUserProfile(String email) {
        return Mono.fromCallable(() -> {
            UserRequest request = UserRequest.newBuilder()
                    .setEmail(email)
                    .build();
            return userStub.getUserProfile(request);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            logger.error("Failed to fetch user profile via gRPC for email: {}. Fallback to default. Error: {}", email, e.getMessage());
            return Mono.just(UserProfileResponse.newBuilder()
                    .setEmail(email)
                    .setFullName("User")
                    .setProfileJson("{}")
                    .build());
        });
    }
}
