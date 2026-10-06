package com.charmi.paymentgateway.provider;

import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class MockPaymentProvider implements PaymentProvider {

    @Value("${payment.mock.delay-ms:500}")
    private long mockDelayMs;

    @Override
    public PaymentProviderResponse charge(Payment payment) {
        log.info("MockPaymentProvider: Processing payment ID: {} for amount: {} {}",
                payment.getId(), payment.getAmount(), payment.getCurrency());

        // Simulate network processing delay
        if (mockDelayMs > 0) {
            try {
                Thread.sleep(mockDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Mock processing interrupted");
            }
        }

        // Deterministic failure rule for testing: fail if amount equals 999.99 or description contains "FAIL"
        boolean isFailure = (payment.getAmount() != null && payment.getAmount().compareTo(new BigDecimal("999.99")) == 0)
                || (payment.getDescription() != null && payment.getDescription().toUpperCase().contains("FAIL"));

        String transactionId = "mock_tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        if (isFailure) {
            log.info("MockPaymentProvider: Payment {} failed via mock provider logic", payment.getId());
            return PaymentProviderResponse.builder()
                    .success(false)
                    .status(PaymentStatus.FAILED)
                    .providerTransactionId(transactionId)
                    .errorMessage("Payment declined by mock provider")
                    .build();
        }

        log.info("MockPaymentProvider: Payment {} succeeded with transaction ID: {}", payment.getId(), transactionId);
        return PaymentProviderResponse.builder()
                .success(true)
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId(transactionId)
                .build();
    }

    @Override
    public String getProviderName() {
        return "MOCK_PAYMENT_PROVIDER";
    }
}
