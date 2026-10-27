package com.lordsai.lsi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Background executor for bulk communication. Deliberately small (one worker) so a bulk send is
 * processed as a controlled, rate-limited stream of batches rather than hundreds of simultaneous
 * SMTP / WhatsApp requests.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "communicationExecutor")
    public Executor communicationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("comm-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
