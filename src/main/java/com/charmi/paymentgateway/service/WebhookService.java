package com.charmi.paymentgateway.service;

import com.charmi.paymentgateway.dto.WebhookRequest;
import com.charmi.paymentgateway.dto.WebhookResponse;
import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.entity.WebhookEvent;
import com.charmi.paymentgateway.exception.InvalidPaymentStateException;
import com.charmi.paymentgateway.exception.ResourceNotFoundException;
import com.charmi.paymentgateway.repository.PaymentRepository;
import com.charmi.paymentgateway.repository.WebhookEventRepository;
import com.charmi.paymentgateway.webhook.WebhookSignatureVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookSignatureVerifier webhookSignatureVerifier;

    @Transactional
    public WebhookResponse processWebhook(WebhookRequest request, String rawBody, String signatureHeader) {
        log.info("Processing webhook event: {} of type: {} for payment ID: {}",
                request.getEventId(), request.getType(), request.getPaymentId());

        // 1. Verify HMAC SHA-256 signature
        webhookSignatureVerifier.verifySignature(rawBody, signatureHeader);

        // 2. Idempotency Check on eventId
        Optional<WebhookEvent> existingEvent = webhookEventRepository.findByEventId(request.getEventId());
        if (existingEvent.isPresent() && existingEvent.get().isProcessed()) {
            log.info("Duplicate webhook event received: {}. Skipping processing.", request.getEventId());
            return WebhookResponse.builder()
                    .eventId(request.getEventId())
                    .status("SKIPPED_DUPLICATE")
                    .message("Event already processed")
                    .build();
        }

        // 3. Find target payment
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for ID: " + request.getPaymentId()));

        // 4. Update Payment status based on webhook event type
        switch (request.getType()) {
            case "payment.succeeded":
                payment.setStatus(PaymentStatus.SUCCESS);
                break;
            case "payment.failed":
                payment.setStatus(PaymentStatus.FAILED);
                break;
            default:
                throw new InvalidPaymentStateException("Unsupported webhook event type: " + request.getType());
        }

        if (request.getProviderTransactionId() != null && !request.getProviderTransactionId().isBlank()) {
            payment.setProviderTransactionId(request.getProviderTransactionId());
        }

        paymentRepository.save(payment);

        // 5. Persist WebhookEvent record
        WebhookEvent event = existingEvent.orElseGet(() -> WebhookEvent.builder()
                .eventId(request.getEventId())
                .eventType(request.getType())
                .paymentId(request.getPaymentId())
                .build());

        event.setProcessed(true);
        webhookEventRepository.save(event);

        log.info("Successfully processed webhook event: {} for payment ID: {}", request.getEventId(), payment.getId());

        return WebhookResponse.builder()
                .eventId(request.getEventId())
                .status("PROCESSED")
                .message("Webhook processed successfully")
                .build();
    }
}
