package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.config.observability.AuthMetrics;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.service.RateLimiterService;
import vn.iotstar.outbox.OutboxEventPublisher;
import vn.iotstar.utils.constants.TopicNames;
import vn.iotstar.utils.dto.EmailOtpRequested;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;
    private final OutboxEventPublisher eventPublisher;
    private final AuthMetrics authMetrics;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_SEND_PER_HOUR = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 10;
    private static final Duration VERIFY_WINDOW = Duration.ofMinutes(15);

    private String otpKey(String email)         { return "otp:" + email; }
    private String sendCntKey(String email)      { return "otp:send:cnt:" + email.toLowerCase(); }
    private String verifyCntKey(String email)    { return "auth:otp:verify:" + email.toLowerCase(); }

    @Override
    public void generateAndDispatch(String userId, String email, String locale, String correlationId) {
        rateLimiterService.checkAndIncrement(sendCntKey(email), MAX_SEND_PER_HOUR, Duration.ofHours(1));

        String otp = generateOtp();
        String hash = passwordEncoder.encode(otp);
        redis.opsForValue().set(otpKey(email), hash, OTP_TTL);

        EmailOtpRequested evt = new EmailOtpRequested(
                UUID.randomUUID().toString(), userId, email,
                Map.of("otp", otp, "expireMinutes", String.valueOf(OTP_TTL.toMinutes()), "locale", locale)
        );
        eventPublisher.publish(TopicNames.SEND_EMAIL, userId, evt);
        authMetrics.getOtpSentCounter().increment();
        log.info("OTP generated & dispatched, userId={}, corrId={}", userId, correlationId);
    }

    @Override
    public boolean verify(String email, String inputOtp) {
        rateLimiterService.checkAndIncrement(verifyCntKey(email), MAX_VERIFY_ATTEMPTS, VERIFY_WINDOW);

        String key = otpKey(email);
        String storedHash = redis.opsForValue().get(key);
        if (storedHash == null) return false;

        boolean ok = passwordEncoder.matches(inputOtp, storedHash);
        if (ok) {
            redis.delete(key);
            rateLimiterService.reset(verifyCntKey(email));
            authMetrics.getOtpVerifiedCounter().increment();
        }
        return ok;
    }

    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        int number = random.nextInt(1000000);
        return String.format("%06d", number);
    }
}
