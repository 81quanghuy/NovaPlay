package vn.iotstar.notificationservice.config.messages;

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
import vn.iotstar.notificationservice.util.TopicNames;
import vn.iotstar.notificationservice.common.dto.EmailOtpRequested;
import vn.iotstar.notificationservice.common.dto.NotificationRequested;
import vn.iotstar.notificationservice.common.dto.UserRegister;
import vn.iotstar.notificationservice.exception.ResourceNotFoundException;

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


    private static final String GROUP_ID = "notification-service";
    private static final int TOPIC_PARTITIONS = 3;
    private static final int LISTENER_CONCURRENCY = 3;

    // ---- Consumer factories: một topic một kiểu payload cụ thể ----
    //
    // USE_TYPE_INFO_HEADERS=false bắt buộc JsonDeserializer ánh xạ về đúng kiểu đã khai báo, bất
    // kể producer có gửi kèm header __TypeId__ hay không — ba topic ở đây mang ba record khác
    // nhau nên không thể dùng chung một ConsumerFactory<String, Object> như user-service.

    @Bean
    public ConsumerFactory<String, EmailOtpRequested> emailOtpConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return consumerFactory(bootstrap, EmailOtpRequested.class);
    }

    @Bean
    public ConsumerFactory<String, UserRegister> userRegisterConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return consumerFactory(bootstrap, UserRegister.class);
    }

    @Bean
    public ConsumerFactory<String, NotificationRequested> notificationRequestedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return consumerFactory(bootstrap, NotificationRequested.class);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(String bootstrap, Class<T> targetType) {
        // Seed từ spring.kafka.* trước, override giá trị riêng của service sau. Tham số của
        // build*Properties là SslBundles và chỉ bị dereference khi có spring.kafka.ssl.bundle —
        // repo dùng ssl.trust-store-location (PEM) chứ không dùng bundle, nên null là an toàn.
        var props = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.notificationservice.*");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(targetType, false));
    }

    // ---- Container factories: cùng chính sách retry/DLT, khác kiểu payload ----

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailOtpRequested> emailOtpKafkaListenerContainerFactory(
            @Qualifier("emailOtpConsumerFactory") ConsumerFactory<String, EmailOtpRequested> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {
        return containerFactory(cf, dltRecoverer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegister> userRegisterKafkaListenerContainerFactory(
            @Qualifier("userRegisterConsumerFactory") ConsumerFactory<String, UserRegister> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {
        return containerFactory(cf, dltRecoverer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationRequested>
    notificationRequestedKafkaListenerContainerFactory(
            @Qualifier("notificationRequestedConsumerFactory") ConsumerFactory<String, NotificationRequested> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {
        return containerFactory(cf, dltRecoverer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(
            ConsumerFactory<String, T> cf, DeadLetterPublishingRecoverer dltRecoverer) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(LISTENER_CONCURRENCY);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10_000);

        var errorHandler = new DefaultErrorHandler(dltRecoverer, backoff);
        // Payload hỏng hoặc tham chiếu tới bản ghi không tồn tại sẽ không tự khỏi khi retry —
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
     * Không dùng được template Boot tự cấu hình: nó dùng ByteArraySerializer, trong khi consumer
     * đã deserialize payload thành object, nên mọi lần publish sang DLT đều ném
     * ClassCastException — message hỏng vẫn mất, chỉ khác là có thêm log. (Đây đúng là lỗi mà
     * email-service mắc phải trước khi gộp vào service này; user-service đã gặp và sửa cùng cách.)
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
                (r, e) -> new TopicPartition(TopicNames.dltOf(r.topic()), r.partition()));
    }

    // ---- Khai báo tường minh các topic: nếu broker tắt auto-create thì recoverer không publish
    // được và message hỏng biến mất đúng lúc cần giữ lại nhất. `send-email.v1` và
    // `activate-account.v1` đã được auth-service tạo trước; khai báo lại ở đây là idempotent
    // (KafkaAdmin bỏ qua topic đã tồn tại) và chỉ có tác dụng khi service này khởi động trước.
    // Lưu ý: auth-service tạo DLT của chúng với 1 partition còn user-service tạo lại với 3 —
    // một bất nhất sẵn có trong repo, không thuộc phạm vi sửa ở đây.

    @Bean
    public NewTopic notificationRequestedTopic() {
        return TopicBuilder.name(TopicNames.NOTIFICATION_REQUESTED)
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic notificationRequestedDltTopic() {
        return TopicBuilder.name(TopicNames.dltOf(TopicNames.NOTIFICATION_REQUESTED))
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic sendEmailDltTopic() {
        return TopicBuilder.name(TopicNames.dltOf(TopicNames.SEND_EMAIL))
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic activateAccountDltTopic() {
        return TopicBuilder.name(TopicNames.dltOf(TopicNames.ACTIVATE_ACCOUNT))
                .partitions(TOPIC_PARTITIONS).replicas(1).build();
    }

    // ---- Container factory riêng cho consumer của các topic .DLT ----
    //
    // Dùng chung factory ở trên sẽ khiến lỗi trong listener DLT bị chính DeadLetterPublishingRecoverer
    // đẩy tiếp sang "<topic>.DLT.DLT" — không ai đọc, coi như mất luôn. Factory này không có
    // error handler tuỳ biến: lỗi rơi về mặc định của Spring Kafka (log rồi bỏ qua bản ghi).

    @Bean
    public ConsumerFactory<String, Object> dltConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        // Seed từ spring.kafka.* trước, override giá trị riêng của service sau. Tham số của
        // build*Properties là SslBundles và chỉ bị dereference khi có spring.kafka.ssl.bundle —
        // repo dùng ssl.trust-store-location (PEM) chứ không dùng bundle, nên null là an toàn.
        var props = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID + "-dlt");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.notificationservice.*");
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(Object.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> dltKafkaListenerContainerFactory(
            @Qualifier("dltConsumerFactory") ConsumerFactory<String, Object> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
