package com.repomedic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class ExecutorConfig {
    @Bean(name = "scanExecutor")
    public Executor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(3); executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("repomedic-job-"); executor.setWaitForTasksToCompleteOnShutdown(true); executor.initialize();
        return executor;
    }
}
