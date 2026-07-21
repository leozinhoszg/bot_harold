package com.promagroup.apibridge.scheduler;

import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.repository.IntegrationRepository;
import com.promagroup.apibridge.service.PollingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicPollSchedulerTest {

    @Mock
    private IntegrationRepository integrationRepository;
    @Mock
    private PollingService pollingService;

    private ThreadPoolTaskScheduler taskScheduler;
    private DynamicPollScheduler scheduler;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.initialize();
        scheduler = new DynamicPollScheduler(taskScheduler, integrationRepository, pollingService);
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
    }

    private Integration integration(long id, String cron, boolean enabled) {
        Integration integration = mock(Integration.class);
        when(integration.getId()).thenReturn(id);
        when(integration.getCron()).thenReturn(cron);
        when(integration.getName()).thenReturn("integ-" + id);
        return integration;
    }

    @Test
    void schedulesEnabledIntegrationAndFiresPoll() {
        Integration integration = integration(1L, "*/1 * * * * *", true);
        when(integration.isEnabled()).thenReturn(true);
        when(integrationRepository.findByEnabledTrue()).thenReturn(List.of(integration));
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        scheduler.scheduleAll();

        // cron a cada 1s -> deve disparar o poll dentro da janela
        verify(pollingService, timeout(3000)).poll(integration);
        assertThat(scheduler.scheduledCount()).isEqualTo(1);
    }

    @Test
    void invalidCronIsIgnoredAndDoesNotSchedule() {
        Integration integration = integration(2L, "isto-nao-e-cron", true);
        when(integrationRepository.findByEnabledTrue()).thenReturn(List.of(integration));

        scheduler.scheduleAll();

        assertThat(scheduler.scheduledCount()).isZero();
        verify(pollingService, after(500).never()).poll(any());
    }
}
