package vn.iotstar.emailservice.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import vn.iotstar.emailservice.service.DedupStore;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.emailservice.service.IKafkaService;
import vn.iotstar.emailservice.util.TopicName;
import vn.iotstar.utils.dto.EmailRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl implements IKafkaService {

    private final IEmailService emailService;

    private final DedupStore dedup; // service SETNX
    private static final Duration DEDUP_TTL = Duration.ofHours(48);

    @KafkaListener(id="email-service-user-registered",
            topics=TopicName.USER_REGISTERED,
            groupId="email-service")
    public void onMessage(@Payload EmailRequest emailRequest, Acknowledgment ack) {
        log.info("Received email evenlt: {}", emailRequest);
        String key = "email:sent:" + UUID.randomUUID();

        // 1) Dedup guard
        if (!dedup.acquireOnce(key, DEDUP_TTL)) {
            // đã gửi/trong tiến trình gửi → skip và commit offset
            ack.acknowledge();
            return;
        }


        try {
            emailService.sendOTP(emailRequest);

            // 3) Thành công → giữ key tới khi TTL hết
            ack.acknowledge();

        } catch (Exception ex) {
            // 4) Thất bại → nhả key để lần retry có thể gửi lại
            dedup.release(key);
            log.error(ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @KafkaListener(id="email-service-forgot-password",
            topics=TopicName.FORGOT_PASSWORD,
            groupId="email-service")
    public void onForgotPassword(@Payload EmailRequest emailRequest, Acknowledgment ack) {
        log.info("Received forgot-password event: {}", emailRequest);
        String key = "email:sent:" + UUID.randomUUID();

        // 1) Dedup guard
        if (!dedup.acquireOnce(key, DEDUP_TTL)) {
            // đã gửi/trong tiến trình gửi → skip và commit offset
            ack.acknowledge();
            return;
        }
        try {
            emailService.sendOTP(emailRequest);

            // 3) Thành công → giữ key tới khi TTL hết
            ack.acknowledge();

        } catch (Exception ex) {
            // 4) Thất bại → nhả key để lần retry có thể gửi lại
            dedup.release(key);
            log.error(ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
}
