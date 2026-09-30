package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.campus.repair.entity.NotificationEntity;
import com.campus.repair.mapper.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

class NotificationServiceTest {
    @Test void duplicateDeliveryIsHarmless() {
        var mapper=mock(NotificationMapper.class);
        var service=new NotificationService(mapper,mock(StudentMapper.class),mock(WorkerMapper.class),
                mock(UserMapper.class),Clock.systemUTC(),mock(ApplicationEventPublisher.class));
        var event=new BusinessNotificationEvent(7,"ASSIGN",11,"已派单","请查看工单","ASSIGN:11:2:42:7");
        when(mapper.insert(any(NotificationEntity.class))).thenReturn(1).thenThrow(new DuplicateKeyException("same event key"));

        assertDoesNotThrow(()->{service.write(event);service.write(event);});
        verify(mapper,times(2)).insert(org.mockito.ArgumentMatchers.<NotificationEntity>argThat(
                row->event.idempotencyKey().equals(row.getIdempotencyKey())));
    }
}
