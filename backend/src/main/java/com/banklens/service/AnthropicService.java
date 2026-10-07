package com.banklens.service;

import com.banklens.dto.AnalysisResponse;
import com.banklens.exception.BankLensExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AnthropicService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_TEXT_CHARS = 20_000;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicService(
            @Value("${banklens.anthropic.api-key}") String apiKey,
            @Value("${banklens.anthropic.model}") String model,
            @Value("${banklens.anthropic.max-tokens}") int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    public AnalysisResponse analyzeStatement(String pdfText) {
        String truncated = pdfText.length() > MAX_TEXT_CHARS
                ? pdfText.substring(0, MAX_TEXT_CHARS)
                : pdfText;

        String prompt = buildPrompt(truncated);
        String rawResponse = callApi(prompt);
        return parseResponse(rawResponse);
    }

    private String buildPrompt(String text) {
        return """
                Analyze this bank statement and return a JSON object matching this schema EXACTLY.
                Return ONLY valid JSON. No markdown, no preamble, no explanation.
                
                {
                  "period": "string",
                  "account": "string",
                  "totalCredits": 0.0,
                  "totalDebits": 0.0,
                  "netFlow": 0.0,
                  "txnCount": 0,
                  "riskScore": 0,
                  "riskLevel": "low|medium|high",
                  "riskDescription": "string",
                  "summary": "string",
                  "categories": [{"name":"string","emoji":"string","amount":0.0,"pct":0}],
                  "anomalies": ["string"],
                  "transactions": [{"date":"string","desc":"string","category":"string","amount":0.0,"type":"credit|debit","flag":false}]
                }
                
                INSTRUCTIONS:
                - Extract EVERY individual transaction line — do NOT group or summarize
                - Each merchant row with a date and amount is one separate transaction
                - Payments are type "credit" with positive amount
                - Purchases are type "debit" with negative amount
                - Flag transactions: out-of-state, large amounts, unrecognized merchants
                - riskScore: 0-100 integer
                - txnCount must equal the number of objects in the transactions array
                
                Statement text:
                """ + text;
    }

    private String callApi(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", "You are a financial analyst. Return ONLY valid JSON, no markdown, no preamble.",
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            String bodyJson = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    log.error("Anthropic API error {}: {}", response.code(), errorBody);
                    throw new BankLensExceptions.AnthropicApiException(
                            "Anthropic API returned " + response.code());
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new BankLensExceptions.AnthropicApiException("Empty response from Anthropic");
                }

                var parsed = objectMapper.readTree(responseBody.string());
                return parsed.path("content").get(0).path("text").asText();
            }
        } catch (IOException e) {
            log.error("Failed to call Anthropic API", e);
            throw new BankLensExceptions.AnthropicApiException("Failed to reach Anthropic API: " + e.getMessage());
        }
    }

    private AnalysisResponse parseResponse(String raw) {
        try {
            String cleaned = raw.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, AnalysisResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response: {}", raw.substring(0, Math.min(500, raw.length())));
            throw new BankLensExceptions.AnthropicApiException("Failed to parse AI response");
        }
    }
}
