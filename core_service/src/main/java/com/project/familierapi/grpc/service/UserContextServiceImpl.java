package com.project.familierapi.grpc.service;

import com.familier.grpc.UserContextServiceGrpc;
import com.familier.grpc.UserRequest;
import com.familier.grpc.UserProfileResponse;
import com.project.familierapi.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;

@GrpcService
@Slf4j
@RequiredArgsConstructor
@Profile("!prod")
public class UserContextServiceImpl extends UserContextServiceGrpc.UserContextServiceImplBase {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void getUserProfile(UserRequest request, StreamObserver<UserProfileResponse> responseObserver) {
        String email = request.getEmail();
        log.info("Fetching user profile for email: {}", email);

        userRepository.findByEmail(email).ifPresentOrElse(
            user -> {
                try {
                    String profileJson = user.getProfile() != null ? objectMapper.writeValueAsString(user.getProfile()) : "{}";
                    UserProfileResponse response = UserProfileResponse.newBuilder()
                            .setEmail(user.getEmail())
                            .setFullName(user.getFullName() != null ? user.getFullName() : "")
                            .setProfileJson(profileJson)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.error("Error serializing user profile", e);
                    responseObserver.onError(e);
                }
            },
            () -> {
                log.warn("User not found: {}", email);
                responseObserver.onError(new RuntimeException("User not found"));
            }
        );
    }
}
