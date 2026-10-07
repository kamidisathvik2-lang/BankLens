package com.banklens.controller;

import com.banklens.dto.AnalysisResponse;
import com.banklens.dto.AnalysisSummaryDto;
import com.banklens.exception.BankLensExceptions;
import com.banklens.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
@DisplayName("AnalysisController")
class AnalysisControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AnalysisService analysisService;
    @MockBean com.banklens.security.JwtUtil jwtUtil;
    @MockBean com.banklens.security.JwtAuthenticationFilter jwtFilter;
    @MockBean com.banklens.repository.UserRepository userRepository;

    private AnalysisResponse mockResponse;
    private UUID analysisId;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();
        mockResponse = new AnalysisResponse();
        mockResponse.setId(analysisId);
        mockResponse.setPeriod("April 2025");
        mockResponse.setAccount("••5223");
        mockResponse.setTotalCredits(5000.0);
        mockResponse.setTotalDebits(3000.0);
        mockResponse.setNetFlow(2000.0);
        mockResponse.setTxnCount(15);
        mockResponse.setRiskScore(25);
        mockResponse.setRiskLevel("low");
        mockResponse.setRiskDescription("Low risk");
        mockResponse.setSummary("Good month");
        mockResponse.setCategories(List.of());
        mockResponse.setAnomalies(List.of());
        mockResponse.setTransactions(List.of());
        mockResponse.setCreatedAt(Instant.now());
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("POST /api/analyses returns 200 with analysis")
    void analyze_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf content".getBytes());

        when(analysisService.analyze(any(), eq("sathvik@example.com"))).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/analyses")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("April 2025"))
                .andExpect(jsonPath("$.riskScore").value(25))
                .andExpect(jsonPath("$.account").value("••5223"));
    }

    @Test
    @DisplayName("POST /api/analyses returns 401 without auth")
    void analyze_unauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/api/analyses").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("POST /api/analyses returns 429 when rate limit exceeded")
    void analyze_rateLimitExceeded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf".getBytes());

        when(analysisService.analyze(any(), anyString()))
                .thenThrow(new BankLensExceptions.RateLimitExceededException("Daily limit reached"));

        mockMvc.perform(multipart("/api/analyses")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("GET /api/analyses returns paginated history")
    void getHistory_success() throws Exception {
        AnalysisSummaryDto summary = AnalysisSummaryDto.builder()
                .id(analysisId)
                .period("April 2025")
                .account("••5223")
                .riskScore(25)
                .riskLevel("low")
                .totalCredits(5000.0)
                .totalDebits(3000.0)
                .createdAt(Instant.now())
                .build();

        when(analysisService.getHistory("sathvik@example.com", 0, 20))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("April 2025"))
                .andExpect(jsonPath("$[0].riskScore").value(25));
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("GET /api/analyses/{id} returns analysis by ID")
    void getAnalysis_success() throws Exception {
        when(analysisService.getAnalysis(analysisId, "sathvik@example.com"))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/analyses/" + analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId.toString()))
                .andExpect(jsonPath("$.period").value("April 2025"));
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("GET /api/analyses/{id} returns 404 for unknown ID")
    void getAnalysis_notFound() throws Exception {
        UUID otherId = UUID.randomUUID();
        when(analysisService.getAnalysis(otherId, "sathvik@example.com"))
                .thenThrow(new BankLensExceptions.AnalysisNotFoundException(otherId.toString()));

        mockMvc.perform(get("/api/analyses/" + otherId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("DELETE /api/analyses/{id} returns 204")
    void deleteAnalysis_success() throws Exception {
        doNothing().when(analysisService).deleteAnalysis(analysisId, "sathvik@example.com");

        mockMvc.perform(delete("/api/analyses/" + analysisId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(analysisService).deleteAnalysis(analysisId, "sathvik@example.com");
    }

    @Test
    @WithMockUser(username = "sathvik@example.com")
    @DisplayName("GET /api/analyses/remaining returns daily quota")
    void getRemaining_success() throws Exception {
        when(analysisService.getRemainingAnalyses("sathvik@example.com")).thenReturn(7L);

        mockMvc.perform(get("/api/analyses/remaining"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(7));
    }
}
