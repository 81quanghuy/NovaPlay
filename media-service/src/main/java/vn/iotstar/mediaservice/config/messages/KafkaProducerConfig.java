package vn.iotstar.mediaservice.config.messages;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import vn.iotstar.mediaservice.util.TopicName;
import vn.iotstar.utils.dto.MediaReadyEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, MediaReadyEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Cấu hình cơ bản, chỉ định địa chỉ broker và cách serialize key/value
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        //================================================================================
        // CÁC CẤU HÌNH CHI TIẾT VỀ ĐỘ TIN CẬY VÀ HIỆU SUẤT
        //================================================================================

        /**
         * Cấu hình mức độ xác nhận (acknowledgment) yêu cầu từ broker.
         *
         * <p><b>Mục đích & Lý do sử dụng "all":</b>
         * Đảm bảo độ bền dữ liệu (durability) ở mức cao nhất. Cài đặt này yêu cầu leader broker
         * phải chờ cho đến khi message được sao chép thành công tới TẤT CẢ các broker bản sao
         * đồng bộ (In-Sync Replicas - ISR) rồi mới gửi xác nhận về cho producer.
         * Điều này đảm bảo message không bị mất ngay cả khi leader broker gặp sự cố.
         * Đây là cài đặt bắt buộc để bật Idempotence và Transaction.
         *
         * <p><b>Đánh đổi:</b> Tăng độ trễ (latency) cho mỗi lần gửi.
         */
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        /**
         * Bật tính năng Idempotent Producer, đảm bảo rằng việc gửi lại message (do retry)
         * sẽ không tạo ra các message trùng lặp trong topic.
         *
         * <p><b>Mục đích & Lý do sử dụng "true":</b>
         * Rất quan trọng để đạt được ngữ nghĩa "chính xác một lần" (exactly-once semantics).
         * Khi `retries` > 0, producer có thể gửi lại một message đã thành công nhưng chưa nhận
         * được xác nhận (do lỗi mạng). Idempotence sẽ giúp broker nhận diện và bỏ qua
         * message bị gửi lại này, tránh trùng lặp dữ liệu.
         *
         * <p><b>Lưu ý:</b> Khi bật `enable.idempotence`, Kafka sẽ tự động ghi đè một số cấu hình
         * khác để đảm bảo an toàn (ví dụ: `acks` sẽ là `all`, `retries` sẽ là số rất lớn).
         */
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        /**
         * Số lần tối đa mà producer sẽ tự động thử gửi lại một message nếu gặp lỗi tạm thời
         * (transient errors) như lỗi mạng hoặc broker đang bầu chọn leader mới.
         *
         * <p><b>Mục đích & Lý do sử dụng "10":</b>
         * Tăng khả năng phục hồi và độ tin cậy của ứng dụng. Một giá trị đủ lớn như 10 giúp
         * producer vượt qua các sự cố ngắn hạn mà không cần can thiệp từ logic ứng dụng.
         */
        configProps.put(ProducerConfig.RETRIES_CONFIG, 10);

        /**
         * Tổng thời gian tối đa (ms) mà một message được phép tồn tại trong hàng đợi của producer
         * cho đến khi được xác nhận thành công, bao gồm cả thời gian cho các lần thử lại.
         *
         * <p><b>Mục đích & Lý do sử dụng "120000":</b>
         * Hoạt động như một cơ chế bảo vệ cuối cùng, ngăn producer bị treo vô thời hạn nếu
         * cluster Kafka gặp sự cố nghiêm trọng trong thời gian dài. Nếu quá 2 phút mà message
         * vẫn chưa được gửi, producer sẽ báo lỗi.
         */
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        /**
         * Số lượng request tối đa mà producer có thể gửi đi trên một kết nối mà chưa cần
         * nhận được xác nhận trả về.
         *
         * <p><b>Mục đích & Lý do sử dụng "5":</b>
         * Cho phép "pipelining" các request để tăng thông lượng. Tuy nhiên, khi bật Idempotence,
         * giá trị này BẮT BUỘC phải nhỏ hơn hoặc bằng 5 để đảm bảo thứ tự của các message
         * không bị xáo trộn trong trường hợp có retry.
         */
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        /**
         * Thời gian (ms) mà producer sẽ trì hoãn việc gửi đi để gom thêm các message khác
         * vào cùng một lô (batch).
         *
         * <p><b>Mục đích & Lý do sử dụng "10":</b>
         * Tối ưu hóa thông lượng (throughput) bằng cách giảm số lượng request mạng. Thay vì
         * gửi từng message riêng lẻ, producer sẽ gom chúng lại và gửi đi trong một request
         * duy nhất. Điều này làm tăng độ trễ một chút nhưng hiệu quả hơn nhiều.
         */
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        /**
         * Kích thước tối đa (bytes) của một lô message được gom lại trước khi gửi đi.
         * Một lô sẽ được gửi khi `linger.ms` hết hạn hoặc khi kích thước của nó đạt đến
         * `batch.size`, tùy điều kiện nào đến trước.
         *
         * <p><b>Mục đích & Lý do sử dụng "32768":</b>
         * Hoạt động cùng với `linger.ms` để tối ưu hóa thông lượng. Kích thước batch lớn hơn
         * giúp nén dữ liệu hiệu quả hơn và giảm overhead của request mạng.
         */
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);

        /**
         * Chỉ định thuật toán nén được sử dụng cho các lô message.
         *
         * <p><b>Mục đích & Lý do sử dụng "lz4":</b>
         * Giảm đáng kể kích thước dữ liệu được gửi qua mạng và lưu trữ trên broker.
         * Điều này giúp tiết kiệm băng thông, giảm dung lượng đĩa và thường cải thiện
         * hiệu suất tổng thể. `lz4` là thuật toán có tốc độ nén/giải nén rất nhanh,
         * là một lựa chọn cân bằng tốt.
         */
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        DefaultKafkaProducerFactory<String, MediaReadyEvent> factory = new DefaultKafkaProducerFactory<>(configProps);

        // Bật tính năng transaction nếu cần.
        factory.setTransactionIdPrefix("tx-");

        return factory;
    }

    @Bean
    public KafkaTemplate<String, MediaReadyEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public NewTopic sendEmail() {
        return TopicBuilder.name(TopicName.SEND_STATUS_MEDIA)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }
}
