package com.banklens.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "period", length = 100)
    private String period;

    @Column(name = "account", length = 100)
    private String account;

    // Full analysis JSON stored as text — flexible, no schema migration on AI changes
    @Column(name = "analysis_json", columnDefinition = "TEXT", nullable = false)
    private String analysisJson;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "total_credits")
    private Double totalCredits;

    @Column(name = "total_debits")
    private Double totalDebits;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
