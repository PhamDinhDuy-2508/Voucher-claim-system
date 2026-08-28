package com.example.voucherclaim.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignControllerContractTest {
    @Test
    void exposesCampaignResourceWithoutVoucherPrefix() {
        RequestMapping mapping = CampaignController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/v1/campaigns");
    }
}
