package vn.iotstar.notificationservice.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import vn.iotstar.utils.events.PushDelivered;
import vn.iotstar.utils.events.PushRequest;

import java.time.Instant;
import java.util.function.Consumer;

@Configuration
public class NotificationConsumers {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumers.class);
    private final ObjectMapper om = new ObjectMapper();
    private final StreamBridge bridge;

    public NotificationConsumers(StreamBridge bridge) {
        this.bridge = bridge;
    }

    @Bean
    public Consumer<Message<String>> notificationPushRequestConsumer() {
        return msg -> {
            try {
                PushRequest req = om.readValue(msg.getPayload(), PushRequest.class);
                log.info("Push {} to user {} via {}", req.template(), req.userId(), req.channel());
                PushDelivered delivered = new PushDelivered(req.requestId(), req.userId(), req.channel(), "DELIVERED", Instant.now());
                bridge.send("ops.notification.delivered.v1", MessageBuilder.withPayload(om.writeValueAsString(delivered)).build());
            } catch (Exception e) {
                throw new RuntimeException("Notification processing failed", e);
            }
        };
    }
}
