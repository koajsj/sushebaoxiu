package com.campus.repair.service;

import java.sql.SQLException;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class BusinessNotificationListener {
    private final NotificationService notifications;
    private final Executor notificationExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(BusinessNotificationEvent event) {
        try {
            // Return to transaction cleanup before REQUIRES_NEW acquires another connection.
            notificationExecutor.execute(() -> write(event));
        } catch (RuntimeException failure) {
            log.error("Notification queue rejected eventId={} key={} eventType={} businessId={} targetUserId={} reason={}",
                    event.eventId(),event.idempotencyKey(),event.eventType(),event.businessId(),event.targetUserId(),failure.getClass().getSimpleName());
        }
    }
    private void write(BusinessNotificationEvent event) {
        try {
            notifications.write(event);
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            String detail = cause instanceof SQLException sql
                    ? "sqlState=" + sql.getSQLState() + ", vendorCode=" + sql.getErrorCode()
                    : "cause=" + cause.getClass().getSimpleName();
            log.error("Notification write failed eventId={} key={} eventType={} businessId={} targetUserId={} reason={} ({})",
                    event.eventId(),event.idempotencyKey(),event.eventType(), event.businessId(), event.targetUserId(), failure.getClass().getSimpleName(), detail);
        }
    }
}
