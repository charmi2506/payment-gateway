package com.charmi.paymentgateway.service;

import com.charmi.paymentgateway.dto.WebhookRequest;
import com.charmi.paymentgateway.dto.WebhookResponse;
import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.entity.WebhookEvent;
import com.charmi.paymentgateway.exception.InvalidWebhookSignatureException;
import com.charmi.paymentgateway.repository.PaymentRepository;
import com.charmi.paymentgateway.repository.WebhookEventRepository;
import com.charmi.paymentgateway.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookSignatureVerifier webhookSignatureVerifier;

    @InjectMocks
    private WebhookService webhookService;

    private WebhookRequest webhookRequest;
    private Payment payment;
    private final String rawBody = "{\"eventId\":\"evt_100\",\"type\":\"payment.succeeded\",\"paymentId\":\"p_123\"}";
    private final String validSignature = "valid_sig";

    @BeforeEach
    void setUp() {
        webhookRequest = WebhookRequest.builder()
                .eventId("evt_100")
                .type("payment.succeeded")
                .paymentId("p_123")
                .providerTransactionId("txn_mock_99")
                .build();

        payment = Payment.builder()
                .id("p_123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .customerEmail("customer@example.com")
                .status(PaymentStatus.PROCESSING)
                .idempotencyKey("key-999")
                .build();
    }

    @Test
    @DisplayName("Should process payment.succeeded webhook successfully")
    void testProcessWebhook_Success() {
        doNothing().when(webhookSignatureVerifier).verifySignature(rawBody, validSignature);
        when(webhookEventRepository.findByEventId("evt_100")).thenReturn(Optional.empty());
        when(paymentRepository.findById("p_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookResponse response = webhookService.processWebhook(webhookRequest, rawBody, validSignature);

        assertNotNull(response);
        assertEquals("PROCESSED", response.getStatus());
        assertEquals("evt_100", response.getEventId());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("txn_mock_99", payment.getProviderTransactionId());

        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should handle duplicate webhook event idempotently")
    void testProcessWebhook_DuplicateEvent() {
        doNothing().when(webhookSignatureVerifier).verifySignature(rawBody, validSignature);
        WebhookEvent processedEvent = WebhookEvent.builder()
                .eventId("evt_100")
                .processed(true)
                .build();
        when(webhookEventRepository.findByEventId("evt_100")).thenReturn(Optional.of(processedEvent));

        WebhookResponse response = webhookService.processWebhook(webhookRequest, rawBody, validSignature);

        assertNotNull(response);
        assertEquals("SKIPPED_DUPLICATE", response.getStatus());
        assertEquals("Event already processed", response.getMessage());

        verify(paymentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw InvalidWebhookSignatureException on signature mismatch")
    void testProcessWebhook_InvalidSignature() {
        doThrow(new InvalidWebhookSignatureException("Invalid signature"))
                .when(webhookSignatureVerifier).verifySignature(rawBody, "invalid_sig");

        assertThrows(InvalidWebhookSignatureException.class,
                () -> webhookService.processWebhook(webhookRequest, rawBody, "invalid_sig"));
    }
}
