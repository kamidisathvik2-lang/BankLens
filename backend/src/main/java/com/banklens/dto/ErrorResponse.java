package com.banklens.dto;
import lombok.*;
import java.time.Instant;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorResponse {
    private String error;
    private String message;
    private int status;
    private Instant timestamp;
}
