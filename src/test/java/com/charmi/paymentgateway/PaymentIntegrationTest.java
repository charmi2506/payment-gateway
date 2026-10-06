package com.charmi.paymentgateway;

import com.charmi.paymentgateway.dto.CreatePaymentRequest;
import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.repository.PaymentRepository;
import com.charmi.paymentgateway.webhook.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookSignatureVerifier signatureVerifier;

    @Value("${webhook.secret:local-secret}")
    private String webhookSecret;

    @Test
    @DisplayName("POST /api/v1/payments - Should create payment and handle Idempotency")
    void testCreatePaymentAndIdempotency() throws Exception {
        String idempotencyKey = "integration-test-key-" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(new BigDecimal("499.99"))
                .currency("INR")
                .customerEmail("customer@example.com")
                .description("Premium Subscription Test")
                .build();

        // 1. First Request
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId", notNullValue()))
                .andExpect(jsonPath("$.amount", is(499.99)))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.customerEmail", is("customer@example.com")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn();

        String responseBody1 = firstResult.getResponse().getContentAsString();
        String paymentId1 = objectMapper.readTree(responseBody1).get("paymentId").asText();

        // 2. Second Request with same Idempotency-Key
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId", is(paymentId1)))
                .andReturn();

        String responseBody2 = secondResult.getResponse().getContentAsString();
        String paymentId2 = objectMapper.readTree(responseBody2).get("paymentId").asText();

        assertEquals(paymentId1, paymentId2);
    }

    @Test
    @DisplayName("POST /api/v1/payments - Should return HTTP 400 on validation failure")
    void testCreatePayment_ValidationFailure() throws Exception {
        CreatePaymentRequest invalidRequest = CreatePaymentRequest.builder()
                .amount(new BigDecimal("-10.00")) // Invalid amount
                .currency("US")                  // Invalid currency length
                .customerEmail("invalid-email")   // Invalid email
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "val-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} - Should retrieve existing payment")
    void testGetPaymentById() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .customerEmail("gettest@example.com")
                .description("Get Payment Test")
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("get-key-" + UUID.randomUUID())
                .build());

        mockMvc.perform(get("/api/v1/payments/" + payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", is(payment.getId())))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.amount", is(150.0)));
    }

    @Test
    @DisplayName("GET /api/v1/payments - Should return paginated payments")
    void testListPayments() throws Exception {
        mockMvc.perform(get("/api/v1/payments?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.pageable", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{id}/refund - Successful & Invalid refund test")
    void testRefundPayment() throws Exception {
        // Create SUCCESS payment
        Payment successPayment = paymentRepository.save(Payment.builder()
                .amount(new BigDecimal("200.00"))
                .currency("INR")
                .customerEmail("refund@example.com")
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("refund-success-key-" + UUID.randomUUID())
                .build());

        // Refund SUCCESS payment -> HTTP 200
        mockMvc.perform(post("/api/v1/payments/" + successPayment.getId() + "/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", is(successPayment.getId())))
                .andExpect(jsonPath("$.status", is("REFUNDED")))
                .andExpect(jsonPath("$.message", is("Payment refunded successfully")));

        // Create PENDING payment
        Payment pendingPayment = paymentRepository.save(Payment.builder()
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .customerEmail("pending@example.com")
                .status(PaymentStatus.PENDING)
                .idempotencyKey("refund-pending-key-" + UUID.randomUUID())
                .build());

        // Attempt to refund PENDING payment -> HTTP 400
        mockMvc.perform(post("/api/v1/payments/" + pendingPayment.getId() + "/refund"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Only payments in SUCCESS state can be refunded")));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/payment - Signature verification and idempotent webhook processing")
    void testWebhookEndpoint() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .customerEmail("webhook@example.com")
                .status(PaymentStatus.PROCESSING)
                .idempotencyKey("webhook-key-" + UUID.randomUUID())
                .build());

        String eventId = "evt_test_" + UUID.randomUUID();
        String payload = String.format("{\"eventId\":\"%s\",\"type\":\"payment.succeeded\",\"paymentId\":\"%s\",\"providerTransactionId\":\"txn_webhook_123\"}",
                eventId, payment.getId());

        String validSignature = signatureVerifier.calculateHmacSha256(payload, webhookSecret);

        // 1. Invalid signature -> HTTP 401
        mockMvc.perform(post("/api/v1/webhooks/payment")
                        .header("X-Webhook-Signature", "invalid-sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid webhook signature")));

        // 2. Valid signature -> HTTP 200 PROCESSED
        mockMvc.perform(post("/api/v1/webhooks/payment")
                        .header("X-Webhook-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId)))
                .andExpect(jsonPath("$.status", is("PROCESSED")));

        // Verify DB payment updated to SUCCESS
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
        assertEquals("txn_webhook_123", updated.getProviderTransactionId());

        // 3. Duplicate Webhook event -> SKIPPED_DUPLICATE
        mockMvc.perform(post("/api/v1/webhooks/payment")
                        .header("X-Webhook-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId)))
                .andExpect(jsonPath("$.status", is("SKIPPED_DUPLICATE")))
                .andExpect(jsonPath("$.message", is("Event already processed")));
    }
}
