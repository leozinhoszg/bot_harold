package com.promagroup.apibridge.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Configura o pool de threads usado pelo agendamento dinamico de polls. */
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${app.integrations.polling-threads:5}") int pollingThreads) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(pollingThreads);
        scheduler.setThreadNamePrefix("poll-");
        // Shutdown gracioso: espera os polls em andamento terminarem (NFR).
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        // Nao chamamos initialize(): o Spring o faz via InitializingBean.
        return scheduler;
    }
}
