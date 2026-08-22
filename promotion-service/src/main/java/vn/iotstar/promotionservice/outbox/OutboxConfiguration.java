package vn.iotstar.promotionservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

/**
 * Khai báo toàn bộ bean của cơ chế outbox.
 * <p>
 * Nằm trong package của service nên component scan tự nhặt — không cần {@code @Import}. Trước đây
 * lớp này ở artifact dùng chung {@code vn.iotstar.outbox}, ngoài vùng scan của mọi service, nên
 * bên dùng phải {@code @Import} tường minh để service Mongo không nạp phải bean cần
 * {@code DataSource} và Kafka. Sau khi outbox thành bản sao riêng của từng service, chỉ service
 * thực sự dùng mới có package này nên vấn đề đó không còn.
 * <p>
 * auth-service giữ một bản sao độc lập chạy trên Boot 4.1, nơi {@code KafkaProperties} đã chuyển
 * sang module {@code spring-boot-kafka} — sửa bên này không tự lan sang bên đó.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    public OutboxDao outboxDao(DataSource dataSource) {
        return new OutboxDao(new JdbcTemplate(dataSource));
    }

    /**
     * Thread pool dùng CHUNG cho mọi lượt relay, bất kể lượt đó được đánh thức bởi hook
     * {@code afterCommit} của publisher hay bởi listener LISTEN. Trước đây pool này nằm bên trong
     * listener, nên tắt LISTEN là mất luôn chỗ chạy của publisher.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService outboxRelayExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(OutboxDao dao, ObjectMapper objectMapper,
                                                     OutboxRelayService relayService,
                                                     OutboxProperties properties,
                                                     ExecutorService outboxRelayExecutor) {
        return new OutboxEventPublisherImpl(dao, objectMapper, relayService, properties, outboxRelayExecutor);
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
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties,
                                                             SslBundles sslBundles) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);
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
     * Sweep định kỳ — lưới an toàn cho row mà pod chết bỏ lại giữa COMMIT và relay. Tắt được bằng
     * {@code novaplay.outbox.sweep-enabled=false}, nhưng tắt rồi thì không còn gì nhặt row mồ côi.
     */
    @Bean
    @ConditionalOnProperty(name = "novaplay.outbox.sweep-enabled", havingValue = "true", matchIfMissing = true)
    public OutboxSweepJob outboxSweepJob(OutboxCatchUp catchUp, OutboxProperties properties) {
        return new OutboxSweepJob(catchUp, properties);
    }

    /**
     * Listener LISTEN/NOTIFY — MẶC ĐỊNH KHÔNG TẠO. Chỉ bật bằng
     * {@code novaplay.outbox.listen-enabled=true} khi connection đi THẲNG tới Postgres; qua
     * connection pooler thì nó chỉ tạo ra vòng reconnect vô tận (xem {@link OutboxProperties}).
     * Bật lên cũng không thay thế được đường {@code afterCommit}, nó chỉ thêm khả năng nhận
     * notification do POD KHÁC phát.
     */
    @Bean
    @ConditionalOnProperty(name = "novaplay.outbox.listen-enabled", havingValue = "true")
    public OutboxNotificationListener outboxNotificationListener(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            OutboxProperties properties, OutboxCatchUp catchUp, OutboxRelayService relayService,
            ExecutorService outboxRelayExecutor) {
        return new OutboxNotificationListener(url, username, password, properties, catchUp,
                relayService, outboxRelayExecutor);
    }

    // OutboxCatchUpController không khai báo ở đây: nó mang @RestController và nay nằm trong
    // package của service nên component scan tự nhặt. Khai báo thêm một @Bean cùng tên chỉ tạo ra
    // hai đường đăng ký cho cùng một controller.
}
