package vn.iotstar.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

/**
 * Khai báo toàn bộ bean của cơ chế outbox.
 * <p>
 * Cố ý nằm ngoài {@code vn.iotstar.utils} và không mang stereotype nào để không bị component scan
 * nhặt phải: mọi service trong repo đều quét {@code vn.iotstar.utils}, kể cả bốn service dùng
 * MongoDB vốn không có {@code DataSource} lẫn Kafka. Service muốn dùng thì {@code @Import} lớp này.
 * <p>
 * Đây là bản dành riêng cho build Gradle (Boot 4.1) — {@code KafkaProperties} đã chuyển sang
 * module {@code spring-boot-kafka}. Bản gốc dùng cho Maven/Boot 3.5 nằm ở src/main/java.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    public OutboxDao outboxDao(DataSource dataSource) {
        return new OutboxDao(new JdbcTemplate(dataSource));
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(OutboxDao dao, ObjectMapper objectMapper) {
        return new OutboxEventPublisherImpl(dao, objectMapper);
    }

    /**
     * KafkaTemplate riêng dùng {@link StringSerializer} cho cả key lẫn value.
     * <p>
     * Bắt buộc phải tách khỏi {@code KafkaTemplate<String, Object>} mặc định: payload đã nằm sẵn
     * trong bảng dưới dạng chuỗi JSON, đưa qua {@code JsonSerializer} sẽ bị bọc thêm một lớp
     * nháy kép nữa. Consumer không bị ảnh hưởng vì notification-service và user-service đều truyền
     * thẳng instance {@code new JsonDeserializer<>(type, false)} vào ConsumerFactory, tức bỏ qua
     * header {@code __TypeId__}.
     */
    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public OutboxMetrics outboxMetrics(MeterRegistry registry, OutboxDao dao,
                                       OutboxProperties properties) {
        return new OutboxMetrics(registry, dao, properties);
    }

    /** Chỉ dùng cho các lượt thử lại một phát của event gửi hỏng — không có tác vụ định kỳ nào. */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService outboxRetryScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "outbox-retry");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public OutboxRelayService outboxRelayService(OutboxDao dao,
                                                 KafkaTemplate<String, String> outboxKafkaTemplate,
                                                 ScheduledExecutorService outboxRetryScheduler,
                                                 OutboxMetrics metrics,
                                                 OutboxProperties properties) {
        return new OutboxRelayService(dao, outboxKafkaTemplate, outboxRetryScheduler, metrics, properties);
    }

    @Bean
    public OutboxCatchUp outboxCatchUp(OutboxDao dao, OutboxRelayService relayService,
                                       OutboxProperties properties) {
        return new OutboxCatchUp(dao, relayService, properties);
    }

    @Bean
    public OutboxNotificationListener outboxNotificationListener(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            OutboxProperties properties, OutboxCatchUp catchUp, OutboxRelayService relayService) {
        return new OutboxNotificationListener(url, username, password, properties, catchUp, relayService);
    }

    @Bean
    public OutboxCatchUpController outboxCatchUpController(OutboxCatchUp catchUp) {
        return new OutboxCatchUpController(catchUp);
    }
}
