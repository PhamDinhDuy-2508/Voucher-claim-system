package com.example.voucherclaim.messaging;

import com.example.voucherclaim.service.ClaimRequestQueueService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ClaimRequestKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(ClaimRequestKafkaConsumer.class);
    private final ClaimRequestQueueService queueService;

    public ClaimRequestKafkaConsumer(ClaimRequestQueueService queueService) {
        this.queueService = queueService;
    }

    /** Kafka accelerates materialization; failure leaves the offset uncommitted for redelivery. */
    @KafkaListener(
            topics = "${app.kafka.claim-request-topic}",
            groupId = "${app.kafka.claim-request-group:voucher-claim-priority-materializer}"
    )
    public void consume(ClaimRequestMessage message) {
        log.debug("ClaimRequested consumed eventId={} requestId={}",
                message.getEventId(), message.getRequestId());
        queueService.materialize(message.getRequestId());
    }
}
