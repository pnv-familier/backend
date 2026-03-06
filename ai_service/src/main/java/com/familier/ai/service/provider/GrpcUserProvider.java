package com.familier.ai.service.provider;

import com.familier.grpc.UserContextServiceGrpc;
import com.familier.grpc.UserProfileResponse;
import com.familier.grpc.UserRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service("internalUserProvider")
@Profile("!prod")
public class GrpcUserProvider implements UserProvider {

    @GrpcClient("usercontext")
    private UserContextServiceGrpc.UserContextServiceBlockingStub userStub;

    @Override
    public Mono<UserProfileResponse> getUserProfile(String email) {
        return Mono.fromCallable(() -> {
            UserRequest request = UserRequest.newBuilder()
                    .setEmail(email)
                    .build();
            return userStub.getUserProfile(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
