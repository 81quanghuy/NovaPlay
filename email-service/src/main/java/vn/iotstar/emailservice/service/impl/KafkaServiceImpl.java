package vn.iotstar.emailservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import vn.iotstar.emailservice.service.DedupService;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.emailservice.util.TopicName;
import vn.iotstar.utils.dto.EmailOtpRequested;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl {

    private final IEmailService emailService;

    private final DedupService dedupService;
    private static final Duration DEDUP_TTL = Duration.ofHours(1);

    @KafkaListener(topics=TopicName.SEND_EMAIL,
            groupId="email-service",
            containerFactory = "emailOtpKafkaListenerContainerFactory")
    public void handle(EmailOtpRequested evt,
            @Header(name = "correlationId", required = false) String correlationId,
            Acknowledgment ack) {

        if (evt == null) throw new IllegalArgumentException("Null payload");
        var vars = evt.variables() == null ? Map.<String,String>of() : evt.variables();
        var otp    = vars.get("otp");
        var expire = vars.getOrDefault("expireMinutes", "5");
        var locale = vars.getOrDefault("locale", "vi-VN");
        if (otp == null || evt.email() == null) {
            throw new IllegalArgumentException("Missing otp/email");
        }

        var msgId = (evt.messageId() == null || evt.messageId().isBlank())
                ? "mid:" + evt.userId() + ":" + System.currentTimeMillis()
                : evt.messageId();

        String dedupKey = "email:sent:" + msgId;

        if (!dedupService.acquireOnce(dedupKey, DEDUP_TTL)) {
            log.info("Skip duplicate msgId={}, userId={}", msgId, evt.userId());
            ack.acknowledge();
            return;
        }

        try {
            emailService.sendOTP(evt.email(), otp, expire, locale);
            log.info("Handled email.send.otp, msgId={}, userId={}, corrId={}",
                    msgId, evt.userId(), correlationId);
            ack.acknowledge();
        } catch (Exception ex) {
            dedupService.release(dedupKey);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
}

