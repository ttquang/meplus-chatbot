package com.ttq.otp;

import com.ttq.otp.MockOtpService.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockOtpServiceTest {

    private final MutableClock clock = new MutableClock();
    private final MockOtpService otp = new MockOtpService(Duration.ofMinutes(5), "123456", clock);

    @Test
    void sendMasksThePhoneNumber() {
        var sent = otp.send("090-000-0001", UUID.randomUUID());
        assertThat(sent.maskedPhone()).isEqualTo("******0001");
        assertThat(sent.expiresInMinutes()).isEqualTo(5);
    }

    @Test
    void theRightCodeVerifiesAndStaysVerifiedForARetry() {
        UUID request = otp.send("0900000001", null).otpRequestId();
        assertThat(otp.verify(request, "000000")).isEqualTo(Result.INVALID_CODE);
        assertThat(otp.verify(request, " 123 456 ")).isEqualTo(Result.VERIFIED);
        assertThat(otp.verify(request, "123456")).isEqualTo(Result.VERIFIED);
    }

    @Test
    void anExpiredCodeIsRefused() {
        UUID request = otp.send("0900000001", null).otpRequestId();
        clock.advance(Duration.ofMinutes(6));
        assertThat(otp.verify(request, "123456")).isEqualTo(Result.EXPIRED);
    }

    @Test
    void tooManyWrongAttemptsLockTheRequest() {
        UUID request = otp.send("0900000001", null).otpRequestId();
        for (int i = 1; i < MockOtpService.MAX_ATTEMPTS; i++) {
            assertThat(otp.verify(request, "000000")).isEqualTo(Result.INVALID_CODE);
        }
        assertThat(otp.verify(request, "000000")).isEqualTo(Result.TOO_MANY_ATTEMPTS);
        assertThat(otp.verify(request, "123456")).isEqualTo(Result.TOO_MANY_ATTEMPTS);
    }

    @Test
    void anUnknownRequestIsRefused() {
        assertThat(otp.verify(UUID.randomUUID(), "123456")).isEqualTo(Result.UNKNOWN_REQUEST);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
