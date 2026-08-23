package vn.iotstar.userservice.config.messages;

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
import vn.iotstar.userservice.util.TopicName;
import vn.iotstar.userservice.exception.ResourceNotFoundException;
import vn.iotstar.userservice.common.dto.UserRegister;

import java.util.HashMap;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

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
    private static final int LISTENER_CONCURRENCY = 3;

    // ---- Consumer factory cho activate-account.v1 ----
    //
    // auth-service publish topic này bằng KafkaTemplate<String,String> (StringSerializer thẳng
    // trên chuỗi JSON đã có sẵn trong outbox, tránh double-encode) — nghĩa là message KHÔNG BAO
    // GIỜ mang header __TypeId__. Trước đây topic này dùng chung một ConsumerFactory<String,
    // Object> với send-status-media.v1 (USE_TYPE_INFO_HEADERS=true, default type Object.class):
    // không có header nên rơi vào nhánh default, Jackson dựng ra LinkedHashMap thay vì
    // UserRegister, Spring không bind được vào tham số @KafkaListener và ném
    // MessageConversionException — mọi message activate-account.v1 phía user-service chắc chắn
    // rơi thẳng vào .DLT. notification-service's KafkaConsumerConfig đã cảnh báo đúng lỗi này
    // ("không thể dùng chung một ConsumerFactory<String, Object> như user-service"); pattern sửa
    // ở đây mượn lại y hệt bên đó: một ConsumerFactory riêng, pin cứng target type,
    // USE_TYPE_INFO_HEADERS=false để không phụ thuộc việc producer có gửi header hay không.
    @Bean
    public ConsumerFactory<String, UserRegister> activateAccountConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        var props = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.userservice.*");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(UserRegister.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegister> activateAccountKafkaListenerContainerFactory(
            @Qualifier("activateAccountConsumerFactory") ConsumerFactory<String, UserRegister> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {
        return listenerContainerFactory(cf, dltRecoverer);
    }

    // ---- Consumer factory cho send-status-media.v1 ----
    //
    // Ngược với activate-account.v1: media-service CÓ gắn header __TypeId__ mang tên logic
    // "mediaReady" (không phải FQCN, vì mỗi service giữ bản MediaReadyEvent riêng trong package
    // của mình). Giữ nguyên cơ chế header + TYPE_MAPPINGS này — đây là ví dụ CLAUDE.md dẫn ra cho
    // cách làm "logical type name" hợp lệ, không phải phần bị lỗi.
    @Bean
    public ConsumerFactory<String, Object> mediaReadyConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        var props = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.userservice.*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "mediaReady:vn.iotstar.userservice.common.dto.MediaReadyEvent");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(Object.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> mediaReadyKafkaListenerContainerFactory(
            @Qualifier("mediaReadyConsumerFactory") ConsumerFactory<String, Object> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {
        return listenerContainerFactory(cf, dltRecoverer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
            ConsumerFactory<String, T> cf, DeadLetterPublishingRecoverer dltRecoverer) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(LISTENER_CONCURRENCY);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10_000);

        var errorHandler = new DefaultErrorHandler(dltRecoverer, backoff);
        // Payload hỏng và tham chiếu tới bản ghi không tồn tại sẽ không tự khỏi khi retry —
        // đẩy thẳng sang DLT thay vì lặp lại 5 lần một cách vô ích.
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                ResourceNotFoundException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * KafkaTemplate riêng cho DLT.
     * <p>
     * Không dùng được template Boot tự cấu hình: nó dùng ByteArraySerializer, trong khi
     * consumer đã deserialize payload thành object (LinkedHashMap), nên mọi lần publish sang
     * DLT đều ném ClassCastException — message hỏng vẫn mất, chỉ khác là có thêm log.
     */
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

    // Khai báo tường minh các topic DLT: nếu broker tắt auto-create thì recoverer sẽ không
    // publish được và message hỏng biến mất đúng vào lúc cần giữ lại nhất.

    @Bean
    public NewTopic activateAccountDltTopic() {
        return TopicBuilder.name(TopicName.ACTIVATE_ACCOUNT + DLT_SUFFIX)
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic sendStatusMediaDltTopic() {
        return TopicBuilder.name(TopicName.SEND_STATUS_MEDIA + DLT_SUFFIX)
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }
}
