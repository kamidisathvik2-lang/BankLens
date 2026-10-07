package com.banklens.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
}
