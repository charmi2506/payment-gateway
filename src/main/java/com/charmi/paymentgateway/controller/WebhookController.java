package com.charmi.paymentgateway.controller;

import com.charmi.paymentgateway.dto.WebhookRequest;
import com.charmi.paymentgateway.dto.WebhookResponse;
import com.charmi.paymentgateway.exception.InvalidPaymentStateException;
import com.charmi.paymentgateway.service.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook Ingestion", description = "Endpoints for receiving payment provider webhooks")
public class WebhookController {

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/payment")
    @Operation(summary = "Process incoming payment webhook", description = "Receives payment event webhooks, verifies HMAC signature, and updates payment state idempotently")
    public ResponseEntity<WebhookResponse> handleWebhook(
            @Parameter(description = "HMAC SHA-256 signature generated using WEBHOOK_SECRET", required = true)
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signatureHeader,
            @RequestBody String rawBody) {

        WebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, WebhookRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidPaymentStateException("Invalid JSON payload in webhook request");
        }

        // Validate DTO constraints manually since raw body string is used for payload signature verification
        Set<ConstraintViolation<WebhookRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Webhook validation failed: ");
            for (ConstraintViolation<WebhookRequest> violation : violations) {
                sb.append("[").append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("] ");
            }
            throw new InvalidPaymentStateException(sb.toString().trim());
        }

        WebhookResponse response = webhookService.processWebhook(request, rawBody, signatureHeader);
        return ResponseEntity.ok(response);
    }
}
