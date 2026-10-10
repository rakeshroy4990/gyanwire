package com.gyanwire.api;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.auth.security.JwtAuthenticationFilter;
import com.gyanwire.config.ApiExceptionHandler;
import com.gyanwire.controller.LlmAdminController;
import com.gyanwire.llm.LlmSummaryService;
import com.gyanwire.plans.WhatIfNarrator;
import com.gyanwire.usage.UsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LlmAdminController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class LlmAdminControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LlmSummaryService summary;

    @MockBean
    UsageService usage;

    @MockBean
    WhatIfNarrator whatIf;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryIsForbiddenWithoutAnAdmin() throws Exception {
        mvc.perform(get("/api/admin/llm/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesTheSpendSummary() throws Exception {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserPrincipal(id, "admin@gyanwire.com", "admin"), null)
        );
        when(usage.isAdmin(id)).thenReturn(true);
        when(summary.summary()).thenReturn(Map.of("capInr", 300));
        mvc.perform(get("/api/admin/llm/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.capInr").value(300));
    }

    @Test
    void whatIfTextReturnsTheNarratorLine() throws Exception {
        when(whatIf.explain(isNull(), eq(12), eq(3)))
                .thenReturn("Estimate. Cost changes by 12 rupees and hours by 3.");
        mvc.perform(post("/api/plan/whatif-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costDelta\":12,\"hoursDelta\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Estimate. Cost changes by 12 rupees and hours by 3."));
    }
}
