package com.charmi.paymentgateway.dto;

import com.charmi.paymentgateway.entity.Payment;
import com.charmi.paymentgateway.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private BigDecimal amount;
    private String currency;
    private String customerEmail;
    private String description;
    private PaymentStatus status;
    private String provider;
    private String providerTransactionId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .customerEmail(payment.getCustomerEmail())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .providerTransactionId(payment.getProviderTransactionId())
                .idempotencyKey(payment.getIdempotencyKey())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
