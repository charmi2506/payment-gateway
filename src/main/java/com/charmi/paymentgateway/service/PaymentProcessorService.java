package com.charmi.paymentgateway.service;

import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.provider.PaymentProvider;
import com.charmi.paymentgateway.provider.PaymentProviderResponse;
import com.charmi.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    @Async
    @Transactional
    public void processPaymentAsync(String paymentId) {
        log.info("Starting async processing for payment ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);

        if (payment == null) {
            log.error("Payment ID {} not found for async processing", paymentId);
            return;
        }

        // Only process if payment is in PENDING state
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Payment ID {} is in status {}, skipping async processing", paymentId, payment.getStatus());
            return;
        }

        // Transition: PENDING -> PROCESSING
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProvider(paymentProvider.getProviderName());
        paymentRepository.save(payment);
        log.info("Payment ID {} transitioned to PROCESSING", paymentId);

        // Charge via PaymentProvider
        PaymentProviderResponse response = paymentProvider.charge(payment);

        // Transition: PROCESSING -> SUCCESS / FAILED
        if (response.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCESS);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        payment.setProviderTransactionId(response.getProviderTransactionId());
        paymentRepository.save(payment);

        log.info("Payment ID {} processing completed with final status: {}", paymentId, payment.getStatus());
    }
}
