package com.example.ordermanagement.api.error;

import java.io.IOException;
import java.net.URI;

import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.ErrorResponse;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            @NonNull AuthenticationException authenticationException) throws IOException {
        ErrorResponse error = ErrorResponse
                .builder(authenticationException, HttpStatus.UNAUTHORIZED,
                        "Authentication is required")
                .title("Unauthorized")
                .instance(URI.create(request.getRequestURI()))
                .build();

        response.setStatus(error.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error.getBody());
    }
}
