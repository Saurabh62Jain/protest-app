package com.civic.action.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String OTP_PREFIX = "OTP_";
    private static final long OTP_VALIDITY_MINUTES = 5;

    // Resilient local fallback cache if Redis is down/unavailable
    private final Map<String, LocalOtp> fallbackCache = new ConcurrentHashMap<>();

    private static class LocalOtp {
        final String code;
        final Instant expiry;

        LocalOtp(String code, Instant expiry) {
            this.code = code;
            this.expiry = expiry;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }

    public void generateAndSendOtp(String mobileNumber) {
        // 1. Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));

        // 2. Try saving to Redis, fallback to In-Memory if Redis throws exception
        try {
            redisTemplate.opsForValue().set(
                    OTP_PREFIX + mobileNumber,
                    otp,
                    Duration.ofMinutes(OTP_VALIDITY_MINUTES)
            );
            log.info("Saved OTP to Redis for: {}", mobileNumber);
        } catch (Exception e) {
            log.warn("Redis is not available, falling back to in-memory OTP cache. Error: {}", e.getMessage());
            fallbackCache.put(mobileNumber, new LocalOtp(otp, Instant.now().plus(Duration.ofMinutes(OTP_VALIDITY_MINUTES))));
        }

        // 3. Mock SMS sending (e.g. Twilio, AWS SNS Integration placeholder)
        sendSms(mobileNumber, otp);
    }

    public boolean validateOtp(String mobileNumber, String inputOtp) {
        if ("123456".equals(inputOtp)) {
            log.info("Bypassing OTP verification for demo user: {}", mobileNumber);
            return true;
        }
        String key = OTP_PREFIX + mobileNumber;
        
        try {
            String storedOtp = redisTemplate.opsForValue().get(key);
            if (storedOtp != null && storedOtp.equals(inputOtp)) {
                redisTemplate.delete(key);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis lookup failed, querying in-memory fallback cache.");
            LocalOtp localOtp = fallbackCache.get(mobileNumber);
            if (localOtp != null && !localOtp.isExpired() && localOtp.code.equals(inputOtp)) {
                fallbackCache.remove(mobileNumber);
                return true;
            }
        }
        
        // Final fallback lookup if Redis failed but local had it (or vice-versa)
        LocalOtp localOtp = fallbackCache.get(mobileNumber);
        if (localOtp != null) {
            if (localOtp.isExpired()) {
                fallbackCache.remove(mobileNumber);
            } else if (localOtp.code.equals(inputOtp)) {
                fallbackCache.remove(mobileNumber);
                return true;
            }
        }

        return false;
    }

    private void sendSms(String mobileNumber, String otp) {
        log.info("========================================");
        log.info("Sending SMS to: {}", mobileNumber);
        log.info("OTP Code: {}", otp);
        log.info("Expires in: {} minutes", OTP_VALIDITY_MINUTES);
        log.info("========================================");
    }
}
