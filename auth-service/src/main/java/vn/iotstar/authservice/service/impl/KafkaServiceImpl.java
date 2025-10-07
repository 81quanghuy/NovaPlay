package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.service.DedupStore;
import vn.iotstar.authservice.service.IAuthService;
import vn.iotstar.authservice.service.IKafkaService;
import vn.iotstar.authservice.util.TopicName;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl implements IKafkaService {

    private final IAuthService authService;

    private final DedupStore dedup; // service SETNX
    private static final Duration DEDUP_TTL = Duration.ofHours(48);

    @KafkaListener(id="auth-service-user-verify-otp",
            topics= TopicName.VERIFY_OTP,
            groupId="auth-service")
    public void onMessage(@Payload String email, Acknowledgment ack) throws RuntimeException {
        log.info("Received email event: {}", email);
        String key = "verifyOTP:sent:" + UUID.randomUUID();

        if (!dedup.acquireOnce(key, DEDUP_TTL)) {
            ack.acknowledge();
            return;
        }
        try {
            authService.verifyEmail(email);
            ack.acknowledge();
        } catch (Exception ex) {
            dedup.release(key);
            log.error(ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
}
