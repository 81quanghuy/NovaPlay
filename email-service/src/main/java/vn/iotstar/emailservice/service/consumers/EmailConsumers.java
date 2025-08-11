package vn.iotstar.emailservice.service.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import vn.iotstar.emailservice.model.entity.Email;
import vn.iotstar.emailservice.repository.EmailRepository;
import vn.iotstar.utils.events.EmailRequest;
import vn.iotstar.utils.events.EmailSent;

import java.time.Instant;
import java.util.Random;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class EmailConsumers {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumers.class);
    private final ObjectMapper om = new ObjectMapper();
    private final StreamBridge bridge;
    private final EmailRepository emailRepository;
    private final Random random = new Random();

    @Bean
    public Consumer<Message<String>> emailSendRequestConsumer() {
        return msg -> {
            try {
                EmailRequest req = om.readValue(msg.getPayload(), EmailRequest.class);

                if (req.to() == null || !req.to().contains("@") || req.template() == null) {
                    throw new IllegalArgumentException("Invalid email request payload");
                }

                if (emailRepository.existsById(req.messageId())) {
                    log.info("Duplicate messageId {}, skipping send", req.messageId());
                    return;
                }

                // Simulate transient failure (10% chance) to demonstrate retry/backoff
                if (random.nextInt(10) == 0) {
                    throw new RuntimeException("Simulated transient failure");
                }

                log.info("Sending email to {} with template {}", req.to(), req.template());
                String providerId = "mock-" + System.currentTimeMillis();

                EmailSent sent = new EmailSent(req.messageId(), req.to(), req.template(), "SENT", providerId, Instant.now());
                bridge.send("ops.email.sent.v1", MessageBuilder.withPayload(om.writeValueAsString(sent)).build());

                Email email = new Email();
                email.setId(req.messageId());

                emailRepository.save(email);

            } catch (IllegalArgumentException permanent) {
                throw permanent; // goes to DLQ
            } catch (Exception transientErr) {
                throw new RuntimeException(transientErr); // triggers retry/backoff
            }
        };
    }
}
