package vn.iotstar.authservice.config.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AuthMetrics {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter registerSuccessCounter;
    private final Counter otpSentCounter;
    private final Counter otpVerifiedCounter;
    private final Counter passwordResetCounter;
    private final Counter rateLimitHitCounter;

    public AuthMetrics(MeterRegistry registry) {
        loginSuccessCounter = Counter.builder("auth.login.success")
                .description("Number of successful logins")
                .register(registry);

        loginFailureCounter = Counter.builder("auth.login.failure")
                .description("Number of failed login attempts")
                .register(registry);

        registerSuccessCounter = Counter.builder("auth.register.success")
                .description("Number of successful registrations")
                .register(registry);

        otpSentCounter = Counter.builder("auth.otp.sent")
                .description("Number of OTPs sent")
                .register(registry);

        otpVerifiedCounter = Counter.builder("auth.otp.verified")
                .description("Number of OTPs verified successfully")
                .register(registry);

        passwordResetCounter = Counter.builder("auth.password.reset")
                .description("Number of password resets")
                .register(registry);

        rateLimitHitCounter = Counter.builder("auth.rate_limit.hit")
                .description("Number of rate limit rejections")
                .register(registry);
    }
}
