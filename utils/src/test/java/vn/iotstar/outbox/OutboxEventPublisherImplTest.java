package vn.iotstar.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherImplTest {

    @Mock private OutboxDao dao;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private OutboxEventPublisherImpl publisher;

    @Test
    void publishChiGhiOutboxVaSerializePayloadThanhJson() {
        UUID generated = UUID.randomUUID();
        when(dao.insert(eq("send-email.v1"), eq("user-1"), any())).thenReturn(generated);

        publisher.publish("send-email.v1", "user-1", Map.of("otp", "123456"));

        verify(dao).insert("send-email.v1", "user-1", "{\"otp\":\"123456\"}");
    }

    @Test
    void publishKhongDuocGuiKafka() {
        // Chốt chặn cho khiếm khuyết nghiêm trọng nhất của bản cũ: EventPublisherImpl gọi
        // kafkaTemplate.send(...).get(30s) NGAY TRONG transaction nghiệp vụ, nên một transaction
        // rollback vẫn để lọt event ra ngoài. Publisher mới không được biết Kafka là gì —
        // thể hiện bằng việc lớp này không có collaborator nào ngoài dao và objectMapper.
        //
        // Lọc field static vì getDeclaredFields() trả về CẢ chúng, mà @Slf4j sinh ra một field
        // `log` static. Và cố ý không dùng .extracting(): nó sinh kiểu capture khiến
        // containsExactlyInAnyOrder(Class<OutboxDao>, ...) không biên dịch được.
        List<? extends Class<?>> kieuCuaFieldInstance = Arrays.stream(
                        OutboxEventPublisherImpl.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();

        assertThat(kieuCuaFieldInstance)
                .containsExactlyInAnyOrder();
    }

    @Test
    void payloadKhongSerializeDuocThiNemLoiDeTransactionRollback() {
        Object khongSerializeDuoc = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                throw new IllegalStateException("bom");
            }
        };

        assertThatThrownBy(() -> publisher.publish("t.v1", "k", khongSerializeDuoc))
                .isInstanceOf(OutboxSerializationException.class);

        verifyNoInteractions(dao);
    }
}
