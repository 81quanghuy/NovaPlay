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

import java.util.HashMap;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String DLT_SUFFIX = ".DLT";
    private static final int TOPIC_PARTITIONS = 3;
    private static final int LISTENER_CONCURRENCY = 3;

    @Bean
    public ConsumerFactory<String, Object> registerUserConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {

        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.iotstar.userservice.*");
        // media-service ghi tên logic "mediaReady" vào header __TypeId__ thay vì FQCN, vì mỗi
        // service nay giữ bản MediaReadyEvent riêng trong package của mình. Ánh xạ ngược tên đó
        // về class cục bộ ở đây; nhờ vậy hai bên đổi cấu trúc package mà không phá hợp đồng wire.
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "mediaReady:vn.iotstar.userservice.common.dto.MediaReadyEvent");

        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(Object.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            @Qualifier("registerUserConsumerFactory") ConsumerFactory<String, Object> cf,
            DeadLetterPublishingRecoverer dltRecoverer) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
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

        var props = new HashMap<String, Object>();
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
