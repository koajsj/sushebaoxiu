package com.campus.repair.service;

import java.sql.SQLException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class BusinessNotificationListener {
    private final NotificationService notifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(BusinessNotificationEvent event) {
        try {
            // Different bean: the REQUIRES_NEW proxy is applied even after the order transaction commits.
            notifications.write(event);
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            String detail = cause instanceof SQLException sql
                    ? "sqlState=" + sql.getSQLState() + ", vendorCode=" + sql.getErrorCode()
                    : "cause=" + cause.getClass().getSimpleName();
            log.error("Notification write failed eventType={} businessId={} targetUserId={} reason={} ({})",
                    event.eventType(), event.businessId(), event.targetUserId(), failure.getClass().getSimpleName(), detail);
        }
    }
}
