package com.cheeseocean.im.common.core.store.session.rocksdb;

import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDbSessionStateStoreTest {

    @Test
    void consumeWsTicketShouldReturnOnceAndDeleteTicket() throws Exception {
        RocksDbSupport support = new RocksDbSupport(Files.createTempDirectory("cheeseim-ticket-test"),
                com.cheeseocean.im.common.core.util.ObjectMapperFactory.createDefaultMapper());
        RocksDbSessionStateStore store = new RocksDbSessionStateStore(support);
        WsTicketPrincipal ticket = new WsTicketPrincipal();
        ticket.setTicket("ticket-1");
        ticket.setExpireAt(System.currentTimeMillis() + 60_000L);

        store.saveWsTicket(ticket, Duration.ofMinutes(1).toMillis());

        WsTicketPrincipal consumed = store.consumeWsTicket("ticket-1");
        WsTicketPrincipal replay = store.consumeWsTicket("ticket-1");

        assertThat(consumed).isNotNull();
        assertThat(consumed.isUsed()).isTrue();
        assertThat(replay).isNull();
    }
}
