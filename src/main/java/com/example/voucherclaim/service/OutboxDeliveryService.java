package com.example.voucherclaim.service;

import java.util.UUID;

/** Publishes one durable outbox event to the configured message broker. */
public interface OutboxDeliveryService {
    /** Locks, dispatches and updates one pending outbox event according to broker acknowledgement. */
    void deliver(UUID eventId);
}
