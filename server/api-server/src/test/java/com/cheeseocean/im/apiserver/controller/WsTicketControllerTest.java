package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.exception.ApiExceptionHandler;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WsTicketControllerTest {

    private SessionIssueService sessionIssueService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessionIssueService = mock(SessionIssueService.class);
        WsTicketController controller = new WsTicketController(sessionIssueService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void issueShouldReturnTicketPayload() throws Exception {
        WsTicketPrincipal principal = new WsTicketPrincipal();
        principal.setTicket("ticket-1");
        principal.setExpireAt(1_776_201_600_000L);
        when(sessionIssueService.issueWsTicket(eq("access"), eq("dev-1"), eq("desktop"), eq("1.0.0")))
                .thenReturn(principal);

        mockMvc.perform(post("/api/im/ws-ticket")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"device_id":"dev-1","platform":"desktop","client_version":"1.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("ticket-1"))
                .andExpect(jsonPath("$.ws_url").value("/ws"))
                .andExpect(jsonPath("$.expire_at").exists());
    }

    @Test
    void invalidBearerShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/im/ws-ticket")
                        .header("Authorization", "Basic access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void illegalStateShouldMapToUnauthorized() throws Exception {
        doThrow(new IllegalStateException("invalid token"))
                .when(sessionIssueService).issueWsTicket(eq("access"), eq(null), eq(null), eq(null));

        mockMvc.perform(post("/api/im/ws-ticket")
                        .header("Authorization", "Bearer access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("invalid token"));
    }
}
