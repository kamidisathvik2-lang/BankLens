package com.banklens.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalysisResponse {
    private UUID id;
    private String period;
    private String account;
    private String fileName;
    private double totalCredits;
    private double totalDebits;
    private double netFlow;
    private int txnCount;
    private int riskScore;
    private String riskLevel;
    private String riskDescription;
    private String summary;
    private List<Category> categories;
    private List<String> anomalies;
    private List<Transaction> transactions;
    private Instant createdAt;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Category {
        private String name;
        private String emoji;
        private double amount;
        private int pct;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Transaction {
        private String date;
        private String desc;
        private String category;
        private double amount;
        private String type;
        private boolean flag;
    }
}
