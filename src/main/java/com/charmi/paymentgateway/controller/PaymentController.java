package com.charmi.paymentgateway.controller;

import com.charmi.paymentgateway.dto.CreatePaymentRequest;
import com.charmi.paymentgateway.dto.PaymentResponse;
import com.charmi.paymentgateway.dto.RefundResponse;
import com.charmi.paymentgateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "Endpoints for creating, retrieving, listing, and refunding payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a new payment", description = "Creates a payment with idempotency support and initiates async processing")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @Parameter(description = "Unique idempotency key to prevent duplicate payments", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details", description = "Retrieves payment details by payment ID")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "ID of the payment to retrieve", required = true)
            @PathVariable String paymentId) {
        
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List payment history", description = "Returns a paginated list of all payments sorted by creation time")
    public ResponseEntity<Page<PaymentResponse>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> response = paymentService.listPayments(pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a payment", description = "Refunds a payment that is in SUCCESS status")
    public ResponseEntity<RefundResponse> refundPayment(
            @Parameter(description = "ID of the payment to refund", required = true)
            @PathVariable String paymentId) {
        
        RefundResponse response = paymentService.refundPayment(paymentId);
        return ResponseEntity.ok(response);
    }
}
