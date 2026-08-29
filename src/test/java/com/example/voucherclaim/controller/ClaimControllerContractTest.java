package com.example.voucherclaim.controller;

import com.example.voucherclaim.model.request.CreateClaimRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimControllerContractTest {
    @Test
    void exposesClaimPathWithOnlyTheNaturalIdempotencyBody() throws Exception {
        Method claimMethod = ClaimController.class.getMethod(
                "claim", CreateClaimRequest.class);

        PostMapping mapping = claimMethod.getAnnotation(PostMapping.class);
        RequestMapping controllerMapping = ClaimController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerMapping.value()).containsExactly("/api/v1/claims");
        assertThat(mapping.value()).isEmpty();
        assertThat(claimMethod.getParameterCount()).isEqualTo(1);
    }

    @Test
    void exposesClaimReadPathUnderTheSameControllerPrefix() throws Exception {
        Method readMethod = ClaimController.class.getMethod(
                "getMyClaim", String.class, String.class);

        GetMapping mapping = readMethod.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/me");
    }

    @Test
    void exposesAsynchronousClaimStatusPath() throws Exception {
        Method statusMethod = ClaimController.class.getMethod("getStatus", String.class);

        GetMapping mapping = statusMethod.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/status");
    }
}
