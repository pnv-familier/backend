package com.project.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.gateway.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Internal Server Error";

        if (ex instanceof ResponseStatusException) {
            HttpStatusCode code = ((ResponseStatusException) ex).getStatusCode();
            status = HttpStatus.valueOf(code.value());
            message = ((ResponseStatusException) ex).getReason();
        } else if (ex.getMessage() != null && (ex.getMessage().contains("Connection refused") || ex.getMessage().contains("Timeout") || ex.getMessage().contains("Connection reset"))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service unavailable or timeout";
            logger.error("Gateway error: Connection failed for path: {}. Error: {}", exchange.getRequest().getPath(), ex.getMessage());
        } else {
            logger.error("Gateway error at path {}: ", exchange.getRequest().getPath(), ex);
        }

        response.setStatusCode(status);
        
        ErrorResponse errorResponse = new ErrorResponse(message != null ? message : ex.getMessage(), exchange.getRequest().getPath().value());
        
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}
