package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.business.repository.DeviceConversationDeliveryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class DeliverySeqPersistenceWriterTest {
    @Test
    void mongoLongFailureShouldKeepCapacityBoundedAndFailExplicitlyWhenBothQueuesAreFull() {
        DeviceConversationDeliveryRepository repository = mock(DeviceConversationDeliveryRepository.class);
        doThrow(new IllegalStateException("mongo unavailable")).when(repository)
                .updateDeliveredSeq("u2", "ios-1", "c3", 3L);
        DeliverySeqPersistenceWriter writer = new DeliverySeqPersistenceWriter(repository, 1, false);

        writer.enqueue("u2", "ios-1", "c1", 1L);
        writer.enqueue("u2", "ios-1", "c2", 2L);

        assertThrows(IllegalStateException.class,
                () -> writer.enqueue("u2", "ios-1", "c3", 3L));
        assertEquals(1, writer.pendingRetryCount());
        writer.shutdown();
    }

    @Test
    void mongoFailureShouldEnterFiniteRetryQueueInsteadOfBeingSwallowed() {
        DeviceConversationDeliveryRepository repository = mock(DeviceConversationDeliveryRepository.class);
        doThrow(new IllegalStateException("mongo unavailable")).when(repository)
                .updateDeliveredSeq("u2", "ios-1", "s:u1:u2", 9L);
        DeliverySeqPersistenceWriter writer = new DeliverySeqPersistenceWriter(repository, false);

        writer.persist(List.of(new DeliverySeqPersistenceWriter.Entry("u2", "ios-1", "s:u1:u2", 9L, 0)));

        assertEquals(1, writer.pendingRetryCount());
    }
}
