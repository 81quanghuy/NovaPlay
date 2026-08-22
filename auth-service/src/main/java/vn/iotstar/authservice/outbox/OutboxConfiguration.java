package vn.iotstar.authservice.outbox;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /**
     * Chỉ tạo khi {@code novaplay.outbox.listen-enabled=true}. Tắt cờ này khi Postgres không hỗ
     * trợ LISTEN/NOTIFY (endpoint qua PgBouncer transaction pooling, hoặc endpoint serverless tự
     * ngủ) — khi đó {@link OutboxPoller} là cơ chế duy nhất đẩy outbox đi.
     * <p>
     * Listener mở connection RIÊNG bằng DriverManager, không mượn từ HikariCP: nó giữ connection
     * vô hạn nên mượn từ pool sẽ làm đói pool. Đây cũng là lý do phải tắt được nó độc lập —
     * connection thứ hai này tính vào hạn mức connection của Postgres managed.
     */
    @Bean
    @ConditionalOnProperty(prefix = "novaplay.outbox", name = "listen-enabled",
            havingValue = "true", matchIfMissing = true)
    public OutboxNotificationListener outboxNotificationListener(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            OutboxProperties properties, OutboxCatchUp catchUp, OutboxRelayService relayService) {
        return new OutboxNotificationListener(url, username, password, properties, catchUp, relayService);
    }

    @Bean
    public OutboxPoller outboxPoller(OutboxCatchUp catchUp, OutboxProperties properties) {
        return new OutboxPoller(catchUp, properties);
    }

    // OutboxCatchUpController không khai báo ở đây: nó mang @RestController và nay nằm trong
    // package của service nên component scan tự nhặt. Khai báo thêm một @Bean cùng tên chỉ tạo ra
    // hai đường đăng ký cho cùng một controller.
}
