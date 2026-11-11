package mx.itsu.emergencias.dispatch.service;

import mx.itsu.emergencias.alert.repo.AlertRepository;
import mx.itsu.emergencias.dispatch.repo.AlertDispatchRepository;
import mx.itsu.emergencias.dispatch.repo.DispatchExpirationRef;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class DispatchExpirationScheduler {

    private final AlertDispatchRepository dispatchRepository;
    private final AlertRepository alertRepository;
    private final AlertDispatchService dispatchService;

    public DispatchExpirationScheduler(AlertDispatchRepository dispatchRepository,
                                       AlertRepository alertRepository,
                                       AlertDispatchService dispatchService) {
        this.dispatchRepository = dispatchRepository;
        this.alertRepository = alertRepository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.expiration-scan-ms:5000}")
    public void expireAndAdvance() {
        Set<UUID> affectedAlerts = new LinkedHashSet<>();
        for (DispatchExpirationRef expired : dispatchRepository.findExpiredReferences(Instant.now())) {
            UUID alertId = dispatchService.expireIfDue(expired.dispatchId());
            if (alertId != null) affectedAlerts.add(alertId);
        }
        affectedAlerts.addAll(alertRepository.findPendingWithoutLiveOffers(Instant.now()));
        for (UUID alertId : affectedAlerts) {
            dispatchService.advanceIfReady(alertId, null);
        }
    }
}
