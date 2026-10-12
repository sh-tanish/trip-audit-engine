package com.example.tripaudit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * Constant-time OTP comparison helper to protect against timing attacks.
 * Neither the entered OTP nor the expected OTP is ever logged.
 */
@Component
public class OtpVerifier {

    public boolean verify(String enteredOtp, String expectedOtp) {
        if (enteredOtp == null || expectedOtp == null) {
            return false;
        }
        byte[] enteredBytes = enteredOtp.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedOtp.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(enteredBytes, expectedBytes);
    }
}
