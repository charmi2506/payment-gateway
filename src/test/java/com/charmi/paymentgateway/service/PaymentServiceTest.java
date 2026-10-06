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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProcessorService paymentProcessorService;

    @InjectMocks
    private PaymentService paymentService;

    private CreatePaymentRequest createRequest;
    private Payment samplePayment;
    private final String idempotencyKey = "key-12345";

    @BeforeEach
    void setUp() {
        createRequest = CreatePaymentRequest.builder()
                .amount(new BigDecimal("499.99"))
                .currency("INR")
                .customerEmail("test@example.com")
                .description("Test Subscription")
                .build();

        samplePayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .amount(createRequest.getAmount())
                .currency(createRequest.getCurrency())
                .customerEmail(createRequest.getCustomerEmail())
                .description(createRequest.getDescription())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should create payment successfully and trigger async processing")
    void testCreatePayment_Success() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);
        doNothing().when(paymentProcessorService).processPaymentAsync(samplePayment.getId());

        PaymentResponse response = paymentService.createPayment(createRequest, idempotencyKey);

        assertNotNull(response);
        assertEquals(samplePayment.getId(), response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(createRequest.getAmount(), response.getAmount());

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(paymentProcessorService, times(1)).processPaymentAsync(samplePayment.getId());
    }

    @Test
    @DisplayName("Should return existing payment when idempotency key matches")
    void testCreatePayment_Idempotency() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.createPayment(createRequest, idempotencyKey);

        assertNotNull(response);
        assertEquals(samplePayment.getId(), response.getPaymentId());
        verify(paymentRepository, never()).save(any());
        verify(paymentProcessorService, never()).processPaymentAsync(any());
    }

    @Test
    @DisplayName("Should throw MissingIdempotencyKeyException when idempotency key is null or blank")
    void testCreatePayment_MissingIdempotencyKey() {
        assertThrows(MissingIdempotencyKeyException.class, () -> paymentService.createPayment(createRequest, null));
        assertThrows(MissingIdempotencyKeyException.class, () -> paymentService.createPayment(createRequest, "  "));
    }

    @Test
    @DisplayName("Should get payment details by ID")
    void testGetPayment_Success() {
        when(paymentRepository.findById(samplePayment.getId())).thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.getPayment(samplePayment.getId());

        assertNotNull(response);
        assertEquals(samplePayment.getId(), response.getPaymentId());
        assertEquals(samplePayment.getCustomerEmail(), response.getCustomerEmail());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when payment ID not found")
    void testGetPayment_NotFound() {
        when(paymentRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPayment("non-existent"));
    }

    @Test
    @DisplayName("Should refund SUCCESS payment successfully")
    void testRefundPayment_Success() {
        samplePayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(samplePayment.getId())).thenReturn(Optional.of(samplePayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = paymentService.refundPayment(samplePayment.getId());

        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals("Payment refunded successfully", response.getMessage());
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException when attempting to refund non-SUCCESS payment")
    void testRefundPayment_InvalidState() {
        samplePayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(samplePayment.getId())).thenReturn(Optional.of(samplePayment));

        assertThrows(InvalidPaymentStateException.class, () -> paymentService.refundPayment(samplePayment.getId()));
    }
}
