package com.example.voucherclaim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "claimWorkerExecutor")
    ThreadPoolTaskExecutor claimWorkerExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Makes thread names searchable in logs and thread dumps.
        executor.setThreadNamePrefix("claim-worker-");
        // Keeps a small warm baseline for normal traffic.
        executor.setCorePoolSize(properties.getPriority().getMinWorkerThreads());
        // Bounds concurrent claim transactions and protects MySQL during bursts.
        executor.setMaxPoolSize(properties.getPriority().getMaxWorkerThreads());
        // Uses direct handoff; Redis remains the only priority/backpressure queue.
        executor.setQueueCapacity(0);
        // Lets graceful shutdown finish claims already handed to workers.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Prevents shutdown from waiting indefinitely on a stuck claim transaction.
        executor.setAwaitTerminationSeconds(20);
        // Applies the configured pool settings and starts accepting worker tasks.
        executor.initialize();
        return executor;
    }
}
