package com.example.voucherclaim.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignControllerContractTest {
    @Test
    void exposesCampaignPathUnderTheApiVersionPrefix() {
        RequestMapping mapping = CampaignController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/v1/campaigns");
    }

    @Test
    void exposesCampaignStatusUnderTheCampaignResource() throws Exception {
        Method method = CampaignController.class.getMethod("getStatus", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/status");
    }
}
