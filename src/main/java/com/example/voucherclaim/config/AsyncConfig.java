package com.example.voucherclaim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "claimWorkerExecutor")
    ThreadPoolTaskExecutor claimWorkerExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("claim-worker-");
        executor.setCorePoolSize(properties.getPriority().getWorkerThreads());
        executor.setMaxPoolSize(properties.getPriority().getWorkerThreads());
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
