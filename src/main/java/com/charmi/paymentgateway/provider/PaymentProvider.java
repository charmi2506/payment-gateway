package com.charmi.paymentgateway.provider;

import com.charmi.paymentgateway.entity.Payment;

public interface PaymentProvider {
    PaymentProviderResponse charge(Payment payment);
    String getProviderName();
}
