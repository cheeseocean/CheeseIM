package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionRepositoryTest {

    @Test
    void findBySessionIdShouldDelegateToSessionStateStore() {
        SessionStateStore store = mock(SessionStateStore.class);
        SessionPrincipal principal = new SessionPrincipal();
        principal.setSessionId("s1");
        when(store.findBySessionId("s1")).thenReturn(principal);

        SessionRepository repository = new SessionRepository(store);

        assertThat(repository.findBySessionId("s1")).isSameAs(principal);
    }
}
