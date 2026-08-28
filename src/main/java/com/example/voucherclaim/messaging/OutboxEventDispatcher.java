package com.example.voucherclaim.messaging;

import com.example.voucherclaim.entity.OutboxEvent;

public interface OutboxEventDispatcher {
    void dispatch(OutboxEvent event);
}
