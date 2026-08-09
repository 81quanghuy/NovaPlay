package vn.iotstar.userservice.service.impl;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.service.EventDedupStore;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.common.dto.MediaReadyEvent;
import vn.iotstar.userservice.common.dto.UserRegister;
import vn.iotstar.userservice.exception.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Các test ở đây bảo vệ một tính chất quan trọng: exception PHẢI thoát ra khỏi listener.
 * <p>
 * Nếu listener bắt và nuốt exception thì {@code DefaultErrorHandler} sẽ không bao giờ chạy,
 * nghĩa là không retry, không có gì vào topic .DLT, và mọi lỗi xử lý trở thành mất message
 * vĩnh viễn trong khi offset vẫn được commit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaServiceImplTest {

    private static final String TOPIC = "activate-account.v1";
    private static final int PARTITION = 0;
    private static final long OFFSET = 42L;

    @Mock private UserProfileService userProfileService;
    @Mock private EventDedupStore dedupStore;
    @Mock private UserMetrics metrics;
    @Mock private Counter counter;

    @InjectMocks private KafkaServiceImpl service;

    @BeforeEach
    void setUp() {
        when(metrics.getKafkaEventProcessed()).thenReturn(counter);
        when(metrics.getKafkaEventFailed()).thenReturn(counter);
        when(metrics.getKafkaEventDuplicate()).thenReturn(counter);
        when(dedupStore.acquireOnce(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("lỗi xử lý phải propagate để error handler đẩy vào DLT")
    void propagatesProcessingFailure() {
        doThrow(new IllegalStateException("mongo down"))
                .when(userProfileService).registerNewUser(any());

        assertThatThrownBy(() -> service.handle(
                new UserRegister("user", "user@example.com"), TOPIC, PARTITION, OFFSET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("nhả cọc dedup khi thất bại để lần retry còn chạy được")
    void releasesDedupKeyOnFailure() {
        doThrow(new IllegalStateException("mongo down"))
                .when(userProfileService).registerNewUser(any());

        assertThatThrownBy(() -> service.handle(
                new UserRegister("user", "user@example.com"), TOPIC, PARTITION, OFFSET))
                .isInstanceOf(IllegalStateException.class);

        // Không nhả cọc thì chính cơ chế dedup sẽ chặn lần retry và event coi như bị bỏ qua.
        verify(dedupStore).release(TOPIC + ":" + PARTITION + ":" + OFFSET);
    }

    @Test
    @DisplayName("bỏ qua event đã xử lý mà không gọi lại service")
    void skipsAlreadyProcessedEvent() {
        when(dedupStore.acquireOnce(anyString())).thenReturn(false);

        service.handle(new UserRegister("user", "user@example.com"), TOPIC, PARTITION, OFFSET);

        verify(userProfileService, never()).registerNewUser(any());
    }

    @Test
    @DisplayName("xử lý thành công event hợp lệ")
    void processesValidEvent() {
        UserRegister evt = new UserRegister("user", "user@example.com");

        service.handle(evt, TOPIC, PARTITION, OFFSET);

        verify(userProfileService).registerNewUser(evt);
        verify(dedupStore, never()).release(anyString());
    }

    @Test
    @DisplayName("payload thiếu email bị từ chối là non-retryable")
    void rejectsPayloadWithoutEmail() {
        assertThatThrownBy(() -> service.handle(
                new UserRegister("user", null), TOPIC, PARTITION, OFFSET))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dedupStore, never()).acquireOnce(anyString());
    }

    @Test
    @DisplayName("payload thiếu username bị từ chối")
    void rejectsPayloadWithoutUsername() {
        assertThatThrownBy(() -> service.handle(
                new UserRegister("  ", "user@example.com"), TOPIC, PARTITION, OFFSET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("media-ready cho user không tồn tại propagate ResourceNotFoundException")
    void propagatesMissingUserOnMediaReady() {
        doThrow(new ResourceNotFoundException("User not found: ghost"))
                .when(userProfileService).changeUrlAvatar(anyString(), anyString());

        assertThatThrownBy(() -> service.handleMediaReadyEvent(
                new MediaReadyEvent("m-1", "ghost", "https://cdn/a.png"),
                "send-status-media.v1", PARTITION, OFFSET))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("media-ready thiếu ownerId bị từ chối")
    void rejectsMediaReadyWithoutOwner() {
        assertThatThrownBy(() -> service.handleMediaReadyEvent(
                new MediaReadyEvent("m-1", null, "https://cdn/a.png"),
                "send-status-media.v1", PARTITION, OFFSET))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
