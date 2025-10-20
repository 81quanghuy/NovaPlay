package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.util.TopicName;
import vn.iotstar.utils.dto.EmailOtpRequested;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafka;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_SEND_PER_HOUR = 5;

    private String otpKey(String email)        { return "otp:" + email; }
    private String sendCntKey(String email)     { return "otp:send:cnt:" + email.toLowerCase(); }

    @Override
    public void generateAndDispatch(String userId, String email, String locale, String correlationId) {
        // Rate limiting: max 5 emails per hour
        Long c = redis.opsForValue().increment(sendCntKey(email));
        if (c != null && c == 1L) redis.expire(sendCntKey(email), Duration.ofHours(1));
        if (c != null && c > MAX_SEND_PER_HOUR) {
            throw new IllegalStateException("Beyond max OTP send limit");
        }
        String otp = generateOtp();

        // Store hashed OTP in Redis with TTL
        String hash = passwordEncoder.encode(otp);
        redis.opsForValue().set(otpKey(email), hash, OTP_TTL);

        EmailOtpRequested evt = new EmailOtpRequested(
                UUID.randomUUID().toString(), userId, email,
                Map.of("otp", otp, "expireMinutes", String.valueOf(OTP_TTL.toMinutes()), "locale", locale)
        );
        kafka.send(TopicName.SEND_EMAIL, userId, evt);
        log.info("OTP generated & dispatched, userId={}, corrId={}", userId, correlationId);
    }

    @Override
    public boolean verify(String email, String inputOtp) {
        String key = otpKey(email);
        String storedHash = redis.opsForValue().get(key);
        if (storedHash == null) return false;

        boolean ok = passwordEncoder.matches(inputOtp, storedHash);
        if (ok) {
            redis.delete(key);
        }
        return ok;
    }

    public String generateOtp() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }
}
