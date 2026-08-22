package vn.iotstar.transcodingworker.config.messages;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import vn.iotstar.transcodingworker.util.TopicNames;

import java.util.HashMap;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

/**
 * Mirror của user-service's {@code KafkaConsumerConfig}, với hai khác biệt cố ý cho một consumer
 * xử lý job dài (transcode một video có thể mất hàng chục phút, không phải mili-giây):
 * <ul>
 *   <li>{@code max.poll.records=1}, {@code concurrency=1} — ffmpeg đã bão hoà CPU của pod, xử lý
 *   song song nhiều video trong cùng một pod chỉ làm chậm tất cả; scale bằng số replica.</li>
 *   <li>{@code max.poll.interval.ms} nới rất rộng — mặc định Kafka (5 phút) sẽ khiến broker coi
 *   consumer là chết và rebalance giữa chừng một job đang chạy hàng chục phút, gây xử lý trùng.</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    /**
     * Các {@code @Bean} bên dưới tự dựng map cấu hình bằng tay, nên nếu không seed từ đây thì
     * MỌI thứ khai trong {@code spring.kafka.*} của application-prod.yml đều bị bỏ qua —
     * security.protocol, sasl.mechanism, sasl.jaas.config, ssl.truststore.*.
     *
     * Triệu chứng khi thiếu (đã gặp thật với Aiven): client in ra
     * {@code security.protocol = PLAINTEXT}, {@code sasl.mechanism = GSSAPI},
     * {@code ssl.truststore.location = null} rồi treo ở bước kết nối, trong khi yml khai đầy đủ
     * và nhìn qua tưởng cấu hình đã có hiệu lực.
     */
    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }


    private static final String DLT_SUFFIX = ".DLT";
    private static final int TOPIC_PARTITIONS = 3;
    private static final int LISTENER_CONCURRENCY = 1;
    private static final int MAX_POLL_INTERVAL_MS = 3 * 60 * 60 * 1000; // 3h — vượt PROCESS_TIMEOUT 2h của FfmpegTranscodeService

    @Bean
    public ConsumerFactory<String, Object> transcodingWorkerConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {

        // Seed từ spring.kafka.* trước, override giá trị riêng của service sau. Tham số của
        // build*Properties là SslBundles và chỉ bị dereference khi có spring.kafka.ssl.bundle —
        // repo dùng ssl.trust-store-location (PEM) chứ không dùng bundle, nên null là an toàn.
        var props = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "transcoding-worker");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.transcodingworker.*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "videoSourceReady:vn.iotstar.transcodingworker.common.dto.VideoSourceReadyEvent");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(Object.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            @Qualifier("transcodingWorkerConsumerFactory") ConsumerFactory<String, Object> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(LISTENER_CONCURRENCY);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10_000);

        var errorHandler = new DefaultErrorHandler(dltRecoverer, backoff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /** Không dùng template Boot tự cấu hình — nó dùng ByteArraySerializer, xem user-service. */
    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {

        // Seed từ spring.kafka.* trước, override giá trị riêng của service sau. Tham số của
        // build*Properties là SslBundles và chỉ bị dereference khi có spring.kafka.ssl.bundle —
        // repo dùng ssl.trust-store-location (PEM) chứ không dùng bundle, nên null là an toàn.
        var props = new HashMap<String, Object>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            @Qualifier("dltKafkaTemplate") KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(template,
                (r, e) -> new TopicPartition(r.topic() + DLT_SUFFIX, r.partition()));
    }

    @Bean
    public NewTopic videoSourceReadyDltTopic() {
        return TopicBuilder.name(TopicNames.VIDEO_SOURCE_READY + DLT_SUFFIX)
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }
}
