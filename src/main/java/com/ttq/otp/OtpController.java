package com.ttq.otp;

import com.ttq.otp.MockOtpService.Result;
import com.ttq.otp.MockOtpService.SentOtp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Mock OTP API: sends a one-time code to a phone number and checks the code the user types back.
 * The chatbot calls it through the API catalog ({@code sendOtp}, {@code verifyOtp}).
 */
@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final MockOtpService otp;

    public OtpController(MockOtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/send")
    public SentOtp send(@Valid @RequestBody SendOtpRequest request) {
        return otp.send(request.phoneNumber(), request.conversationId());
    }

    /** 200 when the code matches; 422 with the reason otherwise, so the caller can ask again. */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyOtpRequest request) {
        Result result = otp.verify(request.otpRequestId(), request.code());
        if (result == Result.VERIFIED) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "OTP not verified: " + result);
        problem.setProperty("reason", result.name());
        return ResponseEntity.of(problem).build();
    }

    public record SendOtpRequest(@NotBlank @Size(max = 50) String phoneNumber, UUID conversationId) {
    }

    public record VerifyOtpRequest(@NotNull UUID otpRequestId, @NotBlank @Size(max = 12) String code) {
    }
}
