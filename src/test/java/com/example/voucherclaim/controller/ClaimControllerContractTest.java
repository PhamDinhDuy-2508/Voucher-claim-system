package com.example.voucherclaim.controller;

import com.example.voucherclaim.model.request.CreateClaimRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimControllerContractTest {
    @Test
    void exposesAssignmentClaimPathAndKeepsIdempotencyHeader() throws Exception {
        Method claimMethod = ClaimController.class.getMethod(
                "claim", String.class, CreateClaimRequest.class);

        PostMapping mapping = claimMethod.getAnnotation(PostMapping.class);
        RequestMapping controllerMapping = ClaimController.class.getAnnotation(RequestMapping.class);
        RequestHeader idempotencyHeader = Arrays.stream(claimMethod.getParameterAnnotations()[0])
                .filter(RequestHeader.class::isInstance)
                .map(RequestHeader.class::cast)
                .findFirst()
                .orElse(null);

        assertThat(controllerMapping.value()).containsExactly("/api/v1/claims");
        assertThat(mapping.value()).isEmpty();
        assertThat(idempotencyHeader).isNotNull();
        assertThat(idempotencyHeader.value()).isEqualTo("Idempotency-Key");
    }

    @Test
    void exposesClaimReadPathUnderTheSameControllerPrefix() throws Exception {
        Method readMethod = ClaimController.class.getMethod(
                "getMyClaim", String.class, String.class);

        GetMapping mapping = readMethod.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/me");
    }
}
