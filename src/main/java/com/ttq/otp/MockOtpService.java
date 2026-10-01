package com.ttq.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the SMS gateway until the real OTP provider is wired in. Codes are kept in memory
 * and logged instead of texted, so a tester can read them from the server log.
 *
 * <p>Replace this with a client for the real provider; the chatbot only sees the OTP endpoints
 * through the API catalog, so the process definition does not change.
 */
@Service
public class MockOtpService {

    private static final Logger log = LoggerFactory.getLogger(MockOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final int MAX_ATTEMPTS = 5;

    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final String fixedCode;
    private final Clock clock;

    @Autowired
    public MockOtpService(@Value("${chatbot.otp.ttl:PT5M}") Duration ttl,
                          @Value("${chatbot.otp.mock-code:}") String fixedCode) {
        this(ttl, fixedCode, Clock.systemUTC());
    }

    MockOtpService(Duration ttl, String fixedCode, Clock clock) {
        this.ttl = ttl;
        this.fixedCode = fixedCode == null || fixedCode.isBlank() ? null : fixedCode.strip();
        this.clock = clock;
    }

    public SentOtp send(String phoneNumber, UUID conversationId) {
        String code = fixedCode != null ? fixedCode : "%06d".formatted(RANDOM.nextInt(1_000_000));
        UUID requestId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(ttl);
        challenges.put(requestId, new Challenge(phoneNumber, code, expiresAt));
        log.info("[MOCK OTP] code {} sent to {} (conversation {}, request {}, expires {})",
                code, mask(phoneNumber), conversationId, requestId, expiresAt);
        return new SentOtp(requestId, mask(phoneNumber), ttl.toSeconds(), Math.ceilDiv(ttl.toSeconds(), 60));
    }

    /**
     * Checking an already verified request with the same code succeeds again, so a retried
     * submission after a later step failed does not lock the user out.
     */
    public Result verify(UUID requestId, String code) {
        Challenge challenge = challenges.get(requestId);
        if (challenge == null) {
            return Result.UNKNOWN_REQUEST;
        }
        synchronized (challenge) {
            String entered = code == null ? "" : code.replaceAll("\\s", "");
            if (challenge.verified) {
                return challenge.code.equals(entered) ? Result.VERIFIED : Result.INVALID_CODE;
            }
            if (clock.instant().isAfter(challenge.expiresAt)) {
                return Result.EXPIRED;
            }
            if (challenge.attempts >= MAX_ATTEMPTS) {
                return Result.TOO_MANY_ATTEMPTS;
            }
            challenge.attempts++;
            if (!challenge.code.equals(entered)) {
                return challenge.attempts >= MAX_ATTEMPTS ? Result.TOO_MANY_ATTEMPTS : Result.INVALID_CODE;
            }
            challenge.verified = true;
            return Result.VERIFIED;
        }
    }

    static String mask(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        return digits.length() <= 4 ? "****" : "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    public record SentOtp(UUID otpRequestId, String maskedPhone, long expiresInSeconds, long expiresInMinutes) {
    }

    public enum Result {
        VERIFIED, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, UNKNOWN_REQUEST
    }

    private static final class Challenge {
        final String phoneNumber;
        final String code;
        final Instant expiresAt;
        int attempts;
        boolean verified;

        Challenge(String phoneNumber, String code, Instant expiresAt) {
            this.phoneNumber = phoneNumber;
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}
