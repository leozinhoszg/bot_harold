package com.promagroup.apibridge.scheduler;

import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.repository.IntegrationRepository;
import com.promagroup.apibridge.service.PollingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Agenda um poll por integracao ativa, cada uma com seu proprio cron (vindo do banco).
 *
 * <p>Usa {@link TaskScheduler} + {@link CronTrigger} (agendamento dinamico), pois cada integracao
 * tem cron proprio — o que {@code @Scheduled} estatico nao cobre. A cada disparo, recarrega a
 * integracao por id (respeitando enable/disable feito em runtime). Desabilitavel via
 * {@code app.scheduler.enabled=false} (usado nos testes).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DynamicPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicPollScheduler.class);

    private final TaskScheduler taskScheduler;
    private final IntegrationRepository integrationRepository;
    private final PollingService pollingService;
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public DynamicPollScheduler(TaskScheduler taskScheduler,
                                IntegrationRepository integrationRepository,
                                PollingService pollingService) {
        this.taskScheduler = taskScheduler;
        this.integrationRepository = integrationRepository;
        this.pollingService = pollingService;
    }

    /** Agenda todas as integracoes ativas assim que a aplicacao fica pronta. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void scheduleAll() {
        for (Integration integration : integrationRepository.findByEnabledTrue()) {
            schedule(integration);
        }
        log.info("Scheduler dinamico: {} integracoes agendadas", scheduled.size());
    }

    /** (Re)agenda uma integracao. Cron invalido e logado e ignorado (nao derruba as demais). */
    public synchronized void schedule(Integration integration) {
        Long id = integration.getId();
        cancel(id);
        try {
            ScheduledFuture<?> future =
                    taskScheduler.schedule(() -> runTick(id), new CronTrigger(integration.getCron()));
            if (future != null) {
                scheduled.put(id, future);
            }
            log.info("Integracao '{}' agendada (cron '{}')", integration.getName(), integration.getCron());
        } catch (IllegalArgumentException e) {
            log.error("Cron invalido '{}' para '{}': {}",
                    integration.getCron(), integration.getName(), e.getMessage());
        }
    }

    public synchronized void cancel(Long integrationId) {
        ScheduledFuture<?> future = scheduled.remove(integrationId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** Recarrega todos os agendamentos (ex.: apos mudanca nas integracoes). */
    public synchronized void reload() {
        scheduled.keySet().forEach(this::cancel);
        scheduleAll();
    }

    int scheduledCount() {
        return scheduled.size();
    }

    private void runTick(Long integrationId) {
        try {
            Integration fresh = integrationRepository.findById(integrationId).orElse(null);
            if (fresh == null || !fresh.isEnabled()) {
                cancel(integrationId);
                return;
            }
            pollingService.poll(fresh);
        } catch (RuntimeException e) {
            // Nunca deixa a excecao matar o agendamento recorrente.
            log.error("Tick da integracao {} falhou", integrationId, e);
        }
    }
}
