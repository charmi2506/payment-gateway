package com.charmi.paymentgateway.webhook;

import com.charmi.paymentgateway.exception.InvalidWebhookSignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class WebhookSignatureVerifier {

    @Value("${webhook.secret:local-secret}")
    private String webhookSecret;

    public void verifySignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook signature header X-Webhook-Signature is missing");
            throw new InvalidWebhookSignatureException("Webhook signature header missing");
        }

        String expectedSignature = calculateHmacSha256(payload, webhookSecret);

        // Normalize if signature has "sha256=" prefix
        String actualSignature = signature.startsWith("sha256=") ? signature.substring(7) : signature;

        if (!constantTimeEquals(expectedSignature.toLowerCase(), actualSignature.toLowerCase())) {
            log.warn("Invalid webhook signature provided. Received: {}, Expected: {}", actualSignature, expectedSignature);
            throw new InvalidWebhookSignatureException("Invalid webhook signature");
        }
    }

    public String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC SHA-256 signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
