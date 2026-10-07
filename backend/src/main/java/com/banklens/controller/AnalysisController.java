package com.banklens.controller;

import com.banklens.dto.AnalysisResponse;
import com.banklens.dto.AnalysisSummaryDto;
import com.banklens.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * Upload a PDF and get AI analysis.
     * Multipart form: field name "file"
     */
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analysisService.analyze(file, userDetails.getUsername()));
    }

    /**
     * Get paginated analysis history for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<AnalysisSummaryDto>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analysisService.getHistory(userDetails.getUsername(), page, size));
    }

    /**
     * Get a specific analysis by ID (must belong to the authenticated user).
     */
    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> getAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analysisService.getAnalysis(id, userDetails.getUsername()));
    }

    /**
     * Delete an analysis.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        analysisService.deleteAnalysis(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Get remaining analyses for today (rate limit info).
     */
    @GetMapping("/remaining")
    public ResponseEntity<Map<String, Long>> getRemaining(
            @AuthenticationPrincipal UserDetails userDetails) {
        long remaining = analysisService.getRemainingAnalyses(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("remaining", remaining));
    }
}
