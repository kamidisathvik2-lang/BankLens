package com.banklens.service;

import com.banklens.dto.AnalysisResponse;
import com.banklens.dto.AnalysisSummaryDto;
import com.banklens.entity.Analysis;
import com.banklens.entity.User;
import com.banklens.exception.BankLensExceptions;
import com.banklens.repository.AnalysisRepository;
import com.banklens.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final PdfParserService pdfParser;
    private final AnthropicService anthropicService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisResponse analyze(MultipartFile file, String userEmail) {
        User user = findUser(userEmail);

        // Enforce rate limit before expensive operations
        rateLimitService.checkAnalysisLimit(user.getId().toString());
        rateLimitService.checkRequestRate(user.getId().toString());

        // Extract text server-side via Apache PDFBox
        String pdfText = pdfParser.extractText(file);
        log.info("PDF extracted: {} chars for user {}", pdfText.length(), userEmail);

        // Call Anthropic API
        AnalysisResponse response = anthropicService.analyzeStatement(pdfText);
        log.info("Analysis complete: {} transactions, risk={}", response.getTxnCount(), response.getRiskScore());

        // Persist to PostgreSQL
        try {
            String analysisJson = objectMapper.writeValueAsString(response);
            Analysis analysis = Analysis.builder()
                    .user(user)
                    .fileName(file.getOriginalFilename())
                    .period(response.getPeriod())
                    .account(maskAccount(response.getAccount()))
                    .analysisJson(analysisJson)
                    .riskScore(response.getRiskScore())
                    .riskLevel(response.getRiskLevel())
                    .totalCredits(response.getTotalCredits())
                    .totalDebits(response.getTotalDebits())
                    .build();

            Analysis saved = analysisRepository.save(analysis);
            response.setId(saved.getId());
            response.setCreatedAt(saved.getCreatedAt());
            // Mask account number before returning
            response.setAccount(saved.getAccount());
        } catch (Exception e) {
            log.error("Failed to persist analysis", e);
            // Still return the analysis even if persistence fails
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<AnalysisSummaryDto> getHistory(String userEmail, int page, int size) {
        User user = findUser(userEmail);
        Page<Analysis> analyses = analysisRepository.findByUserOrderByCreatedAtDesc(
                user, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return analyses.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(UUID id, String userEmail) {
        User user = findUser(userEmail);
        Analysis analysis = analysisRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BankLensExceptions.AnalysisNotFoundException(id.toString()));

        try {
            AnalysisResponse response = objectMapper.readValue(analysis.getAnalysisJson(), AnalysisResponse.class);
            response.setId(analysis.getId());
            response.setCreatedAt(analysis.getCreatedAt());
            return response;
        } catch (Exception e) {
            log.error("Failed to deserialize analysis {}", id, e);
            throw new BankLensExceptions.AnalysisNotFoundException(id.toString());
        }
    }

    @Transactional
    public void deleteAnalysis(UUID id, String userEmail) {
        User user = findUser(userEmail);
        Analysis analysis = analysisRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BankLensExceptions.AnalysisNotFoundException(id.toString()));
        analysisRepository.delete(analysis);
        log.info("Analysis {} deleted by user {}", id, userEmail);
    }

    public long getRemainingAnalyses(String userEmail) {
        User user = findUser(userEmail);
        return rateLimitService.getRemainingAnalyses(user.getId().toString());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BankLensExceptions.InvalidCredentialsException());
    }

    private AnalysisSummaryDto toSummary(Analysis a) {
        return AnalysisSummaryDto.builder()
                .id(a.getId())
                .period(a.getPeriod())
                .account(a.getAccount())
                .fileName(a.getFileName())
                .riskScore(a.getRiskScore() != null ? a.getRiskScore() : 0)
                .riskLevel(a.getRiskLevel())
                .totalCredits(a.getTotalCredits() != null ? a.getTotalCredits() : 0)
                .totalDebits(a.getTotalDebits() != null ? a.getTotalDebits() : 0)
                .createdAt(a.getCreatedAt())
                .build();
    }

    /**
     * Mask account number for storage — keep only last 4 digits.
     * Never store full account numbers.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) return account;
        String digits = account.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            return "••" + digits.substring(digits.length() - 4);
        }
        return account;
    }
}
