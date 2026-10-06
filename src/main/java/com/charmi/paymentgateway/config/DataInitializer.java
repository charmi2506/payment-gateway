package com.charmi.paymentgateway.config;

import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import com.charmi.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PaymentRepository paymentRepository;

    @Override
    public void run(String... args) {
        if (paymentRepository.count() == 0) {
            log.info("Initializing demo payment records...");

            Payment p1 = Payment.builder()
                    .amount(new BigDecimal("1499.00"))
                    .currency("INR")
                    .customerEmail("alice@example.com")
                    .description("Annual Software License")
                    .status(PaymentStatus.SUCCESS)
                    .provider("MOCK_PAYMENT_PROVIDER")
                    .providerTransactionId("mock_tx_demo100000000001")
                    .idempotencyKey("demo-key-001")
                    .build();

            Payment p2 = Payment.builder()
                    .amount(new BigDecimal("299.50"))
                    .currency("USD")
                    .customerEmail("bob@example.com")
                    .description("Cloud Hosting Plan")
                    .status(PaymentStatus.PENDING)
                    .provider("MOCK_PAYMENT_PROVIDER")
                    .idempotencyKey("demo-key-002")
                    .build();

            paymentRepository.save(p1);
            paymentRepository.save(p2);

            log.info("Successfully seeded {} sample payment records.", paymentRepository.count());
        }
    }
}
