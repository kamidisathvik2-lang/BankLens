package com.banklens.service;

import com.banklens.dto.AnalysisResponse;
import com.banklens.dto.AnalysisSummaryDto;
import com.banklens.entity.Analysis;
import com.banklens.entity.User;
import com.banklens.exception.BankLensExceptions;
import com.banklens.repository.AnalysisRepository;
import com.banklens.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService")
class AnalysisServiceTest {

    @Mock AnalysisRepository analysisRepository;
    @Mock UserRepository userRepository;
    @Mock PdfParserService pdfParser;
    @Mock AnthropicService anthropicService;
    @Mock RateLimitService rateLimitService;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks AnalysisService analysisService;

    private User testUser;
    private AnalysisResponse mockAnalysisResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("sathvik@example.com")
                .fullName("Sathvik Kamidi")
                .build();

        mockAnalysisResponse = new AnalysisResponse();
        mockAnalysisResponse.setPeriod("April 2025");
        mockAnalysisResponse.setAccount("4400 6626 2922 5223");
        mockAnalysisResponse.setTotalCredits(5000.0);
        mockAnalysisResponse.setTotalDebits(3000.0);
        mockAnalysisResponse.setNetFlow(2000.0);
        mockAnalysisResponse.setTxnCount(15);
        mockAnalysisResponse.setRiskScore(25);
        mockAnalysisResponse.setRiskLevel("low");
        mockAnalysisResponse.setRiskDescription("Low risk account");
        mockAnalysisResponse.setSummary("Good financial health");
        mockAnalysisResponse.setCategories(List.of());
        mockAnalysisResponse.setAnomalies(List.of());
        mockAnalysisResponse.setTransactions(List.of());
    }

    @Test
    @DisplayName("analyze: full happy path returns analysis with ID")
    void analyze_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf content".getBytes());

        when(userRepository.findByEmail("sathvik@example.com")).thenReturn(Optional.of(testUser));
        when(pdfParser.extractText(any(MultipartFile.class))).thenReturn("Extracted PDF text...");
        when(anthropicService.analyzeStatement(anyString())).thenReturn(mockAnalysisResponse);

        Analysis savedAnalysis = Analysis.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .period("April 2025")
                .account("••5223")
                .riskScore(25)
                .riskLevel("low")
                .totalCredits(5000.0)
                .totalDebits(3000.0)
                .analysisJson(objectMapper.writeValueAsString(mockAnalysisResponse))
                .createdAt(Instant.now())
                .build();

        when(analysisRepository.save(any(Analysis.class))).thenReturn(savedAnalysis);

        AnalysisResponse result = analysisService.analyze(file, "sathvik@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getPeriod()).isEqualTo("April 2025");
        assertThat(result.getRiskScore()).isEqualTo(25);
        assertThat(result.getId()).isNotNull();

        verify(rateLimitService).checkAnalysisLimit(testUser.getId().toString());
        verify(rateLimitService).checkRequestRate(testUser.getId().toString());
        verify(pdfParser).extractText(file);
        verify(anthropicService).analyzeStatement("Extracted PDF text...");
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("analyze: masks account number before saving")
    void analyze_masksAccount() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf".getBytes());

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(pdfParser.extractText(any())).thenReturn("text");
        when(anthropicService.analyzeStatement(anyString())).thenReturn(mockAnalysisResponse);
        when(analysisRepository.save(any())).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            // Verify account is masked
            assertThat(a.getAccount()).startsWith("••");
            assertThat(a.getAccount()).doesNotContain("4400");
            a.setId(UUID.randomUUID());
            a.setCreatedAt(Instant.now());
            return a;
        });

        analysisService.analyze(file, "sathvik@example.com");
        verify(analysisRepository).save(any());
    }

    @Test
    @DisplayName("analyze: throws when rate limit exceeded")
    void analyze_rateLimitExceeded() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "s.pdf", "application/pdf", "pdf".getBytes());

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        doThrow(new BankLensExceptions.RateLimitExceededException("Daily limit reached"))
                .when(rateLimitService).checkAnalysisLimit(anyString());

        assertThatThrownBy(() -> analysisService.analyze(file, "sathvik@example.com"))
                .isInstanceOf(BankLensExceptions.RateLimitExceededException.class);

        verify(pdfParser, never()).extractText(any());
        verify(anthropicService, never()).analyzeStatement(any());
    }

    @Test
    @DisplayName("analyze: throws when user not found")
    void analyze_userNotFound() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "s.pdf", "application/pdf", "pdf".getBytes());

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.analyze(file, "unknown@example.com"))
                .isInstanceOf(BankLensExceptions.InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("getHistory: returns paginated summaries")
    void getHistory_returnsSummaries() {
        Analysis analysis = Analysis.builder()
                .id(UUID.randomUUID())
                .period("April 2025")
                .account("••5223")
                .riskScore(25)
                .riskLevel("low")
                .totalCredits(5000.0)
                .totalDebits(3000.0)
                .createdAt(Instant.now())
                .build();

        Page<Analysis> page = new PageImpl<>(List.of(analysis));
        when(userRepository.findByEmail("sathvik@example.com")).thenReturn(Optional.of(testUser));
        when(analysisRepository.findByUserOrderByCreatedAtDesc(eq(testUser), any(Pageable.class)))
                .thenReturn(page);

        List<AnalysisSummaryDto> result = analysisService.getHistory("sathvik@example.com", 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriod()).isEqualTo("April 2025");
        assertThat(result.get(0).getRiskScore()).isEqualTo(25);
    }

    @Test
    @DisplayName("getAnalysis: throws when analysis belongs to different user")
    void getAnalysis_wrongUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByEmail("sathvik@example.com")).thenReturn(Optional.of(testUser));
        when(analysisRepository.findByIdAndUser(eq(id), eq(testUser))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.getAnalysis(id, "sathvik@example.com"))
                .isInstanceOf(BankLensExceptions.AnalysisNotFoundException.class);
    }

    @Test
    @DisplayName("deleteAnalysis: removes analysis from repository")
    void deleteAnalysis_success() {
        UUID id = UUID.randomUUID();
        Analysis analysis = Analysis.builder().id(id).user(testUser).build();

        when(userRepository.findByEmail("sathvik@example.com")).thenReturn(Optional.of(testUser));
        when(analysisRepository.findByIdAndUser(id, testUser)).thenReturn(Optional.of(analysis));

        analysisService.deleteAnalysis(id, "sathvik@example.com");

        verify(analysisRepository).delete(analysis);
    }
}
