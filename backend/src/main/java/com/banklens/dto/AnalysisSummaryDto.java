package com.banklens.dto;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalysisSummaryDto {
    private UUID id;
    private String period;
    private String account;
    private String fileName;
    private int riskScore;
    private String riskLevel;
    private double totalCredits;
    private double totalDebits;
    private Instant createdAt;
}
