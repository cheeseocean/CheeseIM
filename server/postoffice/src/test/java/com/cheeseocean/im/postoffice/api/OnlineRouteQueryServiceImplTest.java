package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnlineRouteQueryServiceImplTest {

    @Test
    void shouldReturnRpcSafeMutableListWhenNoRouteExists() {
        OnlineRouteService onlineRouteService = mock(OnlineRouteService.class);
        when(onlineRouteService.findByUser("offline-user")).thenReturn(List.of());

        List<?> routes = new OnlineRouteQueryServiceImpl(onlineRouteService).findByUser("offline-user");

        assertInstanceOf(ArrayList.class, routes);
    }
}
