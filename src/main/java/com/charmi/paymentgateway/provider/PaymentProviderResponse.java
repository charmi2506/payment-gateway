package com.charmi.paymentgateway.provider;

import com.charmi.paymentgateway.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProviderResponse {
    private boolean success;
    private PaymentStatus status;
    private String providerTransactionId;
    private String errorMessage;
}
