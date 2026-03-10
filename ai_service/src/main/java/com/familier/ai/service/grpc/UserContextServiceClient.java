package com.familier.ai.service.grpc;

import com.familier.grpc.UserContextServiceGrpc;
import com.familier.grpc.UserRequest;
import com.familier.grpc.UserProfileResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Profile("!prod")
public class UserContextServiceClient {

    @GrpcClient("usercontext")
    private UserContextServiceGrpc.UserContextServiceBlockingStub userStub;

    public Mono<UserProfileResponse> getUserProfile(String email) {
        return Mono.fromCallable(() -> {
            UserRequest request = UserRequest.newBuilder()
                    .setEmail(email)
                    .build();
            return userStub.getUserProfile(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
