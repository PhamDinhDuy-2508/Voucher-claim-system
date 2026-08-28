package com.example.voucherclaim.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    /** Creates the local/dev topic; production may provision the same topic via infrastructure. */
    @Bean
    public NewTopic notificationTopic(AppProperties properties) {
        int partitions = properties.getKafka().getNotificationTopicPartitions();
        if (partitions < 1) {
            throw new IllegalStateException("app.kafka.notification-topic-partitions must be at least 1");
        }
        return TopicBuilder.name(properties.getKafka().getNotificationTopic())
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
