package com.charmi.paymentgateway.service;

import com.charmi.paymentgateway.dto.CreatePaymentRequest;
import com.charmi.paymentgateway.dto.PaymentResponse;
import com.charmi.paymentgateway.dto.RefundResponse;
import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.exception.InvalidPaymentStateException;
import com.charmi.paymentgateway.exception.MissingIdempotencyKeyException;
import com.charmi.paymentgateway.exception.ResourceNotFoundException;
import com.charmi.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessorService paymentProcessorService;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");
        }

        // Check for existing payment with same idempotency key
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Idempotent request detected for key: {}. Returning existing payment ID: {}",
                    idempotencyKey, existingPayment.get().getId());
            return PaymentResponse.fromEntity(existingPayment.get());
        }

        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .customerEmail(request.getCustomerEmail())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Created payment record with ID: {} and Idempotency-Key: {}", savedPayment.getId(), idempotencyKey);

        // Trigger async processing
        paymentProcessorService.processPaymentAsync(savedPayment.getId());

        return PaymentResponse.fromEntity(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + paymentId));
        return PaymentResponse.fromEntity(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable)
                .map(PaymentResponse::fromEntity);
    }

    @Transactional
    public RefundResponse refundPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            log.warn("Failed refund attempt for payment ID: {}. Current status: {}", paymentId, payment.getStatus());
            throw new InvalidPaymentStateException(
                    "Only payments in SUCCESS state can be refunded. Current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment updatedPayment = paymentRepository.save(payment);
        log.info("Payment ID: {} successfully refunded", paymentId);

        return RefundResponse.builder()
                .paymentId(updatedPayment.getId())
                .status(updatedPayment.getStatus())
                .amount(updatedPayment.getAmount())
                .currency(updatedPayment.getCurrency())
                .updatedAt(updatedPayment.getUpdatedAt())
                .message("Payment refunded successfully")
                .build();
    }
}
