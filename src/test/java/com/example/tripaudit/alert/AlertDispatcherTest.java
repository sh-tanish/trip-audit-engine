package com.example.tripaudit.alert;

import com.example.tripaudit.config.AuditMetrics;
import com.example.tripaudit.domain.AlertChannel;
import com.example.tripaudit.domain.AlertDelivery;
import com.example.tripaudit.domain.DeliveryStatus;
import com.example.tripaudit.domain.Severity;
import com.example.tripaudit.domain.Violation;
import com.example.tripaudit.domain.ViolationType;
import com.example.tripaudit.repository.AlertDeliveryRepository;
import com.example.tripaudit.repository.ViolationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertDispatcherTest {

    private NotifierFactory notifierFactory;
    private AlertDeliveryRepository alertDeliveryRepository;
    private ViolationRepository violationRepository;
    private AuditMetrics auditMetrics;
    private AlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notifierFactory = mock(NotifierFactory.class);
        alertDeliveryRepository = mock(AlertDeliveryRepository.class);
        violationRepository = mock(ViolationRepository.class);
        auditMetrics = new AuditMetrics(new SimpleMeterRegistry());
        dispatcher = new AlertDispatcher(notifierFactory, alertDeliveryRepository, violationRepository, auditMetrics);
    }

    @Test
    @DisplayName("Successful dispatch marks AlertDelivery as SENT and saves it")
    void successfulDispatchMarksSent() {
        Notifier mockNotifier = mock(Notifier.class);
        when(notifierFactory.forChannel(AlertChannel.TRANSPORT_MANAGER)).thenReturn(mockNotifier);

        Violation violation = new Violation(
                1L, ViolationType.NIGHT_SAFETY, Severity.HIGH, "NightSafetyRule",
                "stop-1", 10L, 20L, "Night safety evidence", Instant.now(), "run-1"
        );
        AlertDelivery delivery = new AlertDelivery(100L, AlertChannel.TRANSPORT_MANAGER, Instant.now());

        dispatcher.dispatch(violation, List.of(delivery));

        verify(mockNotifier, times(1)).send(any(AlertMessage.class));
        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertNull(delivery.getLastError());
        verify(alertDeliveryRepository, times(1)).save(delivery);
    }

    @Test
    @DisplayName("Notifier failure marks delivery FAILED, increments metric, and does not propagate exception")
    void notifierFailureMarksFailedWithoutThrowing() {
        Notifier failingNotifier = mock(Notifier.class);
        doThrow(new RuntimeException("SMS gateway timeout")).when(failingNotifier).send(any(AlertMessage.class));
        when(notifierFactory.forChannel(AlertChannel.CONTROL_ROOM)).thenReturn(failingNotifier);

        Violation violation = new Violation(
                1L, ViolationType.SOS, Severity.CRITICAL, "SOS",
                "emp-1:12345", null, 1L, "Emergency panic press", Instant.now(), "run-sos"
        );
        AlertDelivery delivery = new AlertDelivery(200L, AlertChannel.CONTROL_ROOM, Instant.now());

        assertDoesNotThrow(() -> dispatcher.dispatch(violation, List.of(delivery), 12.97, 77.59));

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(RuntimeException.class.getName(), delivery.getLastError());
        verify(alertDeliveryRepository, times(1)).save(delivery);

        // Verify failure counter incremented
        double failedCount = auditMetrics.getMeterRegistry()
                .get("alerts_failed_total")
                .counter()
                .count();
        assertEquals(1.0, failedCount);
    }

    @Test
    @DisplayName("Failing alertDeliveryRepository.save does not break dispatch and remaining deliveries continue")
    void failingSaveDoesNotBreakDispatch() {
        Notifier mockNotifier1 = mock(Notifier.class);
        Notifier mockNotifier2 = mock(Notifier.class);
        when(notifierFactory.forChannel(AlertChannel.TRANSPORT_MANAGER)).thenReturn(mockNotifier1);
        when(notifierFactory.forChannel(AlertChannel.CONTROL_ROOM)).thenReturn(mockNotifier2);

        doThrow(new RuntimeException("DB connection error")).when(alertDeliveryRepository).save(any(AlertDelivery.class));

        Violation violation = new Violation(
                1L, ViolationType.SOS, Severity.CRITICAL, "SOS",
                "emp-1:12345", null, 1L, "Emergency panic press", Instant.now(), "run-sos"
        );
        AlertDelivery d1 = new AlertDelivery(201L, AlertChannel.TRANSPORT_MANAGER, Instant.now());
        AlertDelivery d2 = new AlertDelivery(202L, AlertChannel.CONTROL_ROOM, Instant.now());

        assertDoesNotThrow(() -> dispatcher.dispatch(violation, List.of(d1, d2)));

        verify(mockNotifier1, times(1)).send(any(AlertMessage.class));
        verify(mockNotifier2, times(1)).send(any(AlertMessage.class));
    }

    @Test
    @DisplayName("dispatchPending recovers and dispatches unhandled PENDING deliveries")
    void dispatchPendingRecoversPendingDeliveries() {
        Notifier mockNotifier = mock(Notifier.class);
        when(notifierFactory.forChannel(AlertChannel.DASHBOARD)).thenReturn(mockNotifier);

        AlertDelivery pendingDelivery = new AlertDelivery(300L, AlertChannel.DASHBOARD, Instant.now());
        when(alertDeliveryRepository.findByStatus(DeliveryStatus.PENDING)).thenReturn(List.of(pendingDelivery));

        Violation violation = new Violation(
                2L, ViolationType.OVER_SPEED, Severity.MEDIUM, "OverSpeedRule",
                "run-100", null, null, "Minor speeding", Instant.now(), "run-audit"
        );
        when(violationRepository.findById(300L)).thenReturn(Optional.of(violation));

        dispatcher.dispatchPending();

        verify(mockNotifier, times(1)).send(any(AlertMessage.class));
        assertEquals(DeliveryStatus.SENT, pendingDelivery.getStatus());
        verify(alertDeliveryRepository, times(1)).save(pendingDelivery);
    }
}
