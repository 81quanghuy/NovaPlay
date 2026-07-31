package vn.iotstar.notificationservice.dispatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.notificationservice.channel.NotificationChannel;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.DedupStore;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private NotificationChannel emailChannel;
    @Mock private NotificationChannel inAppChannel;
    @Mock private DedupStore dedupStore;
    @Mock private NotificationMetrics metrics;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(emailChannel.type()).thenReturn(ChannelType.EMAIL);
        when(inAppChannel.type()).thenReturn(ChannelType.IN_APP);
        dispatcher = new NotificationDispatcher(List.of(emailChannel, inAppChannel), dedupStore, metrics);
    }

    private static NotificationRequest requestFor(ChannelType... channels) {
        return NotificationRequest.builder()
                .dedupKey("evt-1")
                .userEmail("user@novaplay.dev")
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .channels(EnumSet.copyOf(List.of(channels)))
                .build();
    }

    @Test
    void gui_thanh_cong_qua_ca_hai_kenh() {
        when(dedupStore.acquireOnce(anyString(), any(Duration.class))).thenReturn(true);

        dispatcher.dispatch(requestFor(ChannelType.EMAIL, ChannelType.IN_APP));

        verify(emailChannel).send(any());
        verify(inAppChannel).send(any());
        verify(dedupStore).acquireOnce("evt-1:EMAIL", Duration.ofHours(24));
        verify(dedupStore).acquireOnce("evt-1:IN_APP", Duration.ofHours(24));
        verify(metrics).channelSent(ChannelType.EMAIL);
        verify(metrics).channelSent(ChannelType.IN_APP);
    }

    @Test
    void bo_qua_kenh_da_gui_truoc_do() {
        when(dedupStore.acquireOnce("evt-1:EMAIL", Duration.ofHours(24))).thenReturn(false);

        dispatcher.dispatch(requestFor(ChannelType.EMAIL));

        verify(emailChannel, never()).send(any());
        verify(metrics).channelSkipped(ChannelType.EMAIL);
    }

    @Test
    void mot_kenh_fail_khong_can_kenh_con_lai_va_khong_release_khoa_da_thanh_cong() {
        when(dedupStore.acquireOnce(anyString(), any(Duration.class))).thenReturn(true);
        RuntimeException boom = new RuntimeException("SMTP down");
        org.mockito.Mockito.doThrow(boom).when(emailChannel).send(any());
        when(emailChannel.isRetryable(boom)).thenReturn(true);

        assertThatThrownBy(() -> dispatcher.dispatch(requestFor(ChannelType.EMAIL, ChannelType.IN_APP)))
                .isInstanceOf(ChannelDeliveryException.class)
                .satisfies(ex -> assertThat(((ChannelDeliveryException) ex).failures())
                        .containsOnlyKeys(ChannelType.EMAIL));

        // Kênh còn lại vẫn phải được thử, không được bỏ qua chỉ vì kênh trước đó fail.
        verify(inAppChannel, times(1)).send(any());
        // Kênh lỗi được nhả khoá để lần retry của Kafka còn xử lý lại được...
        verify(dedupStore).release("evt-1:EMAIL");
        // ...nhưng kênh đã thành công thì giữ nguyên khoá, không được nhả.
        verify(dedupStore, never()).release("evt-1:IN_APP");
    }

    @Test
    void khong_release_khoa_khi_kenh_bao_khong_the_thu_lai() {
        when(dedupStore.acquireOnce(anyString(), any(Duration.class))).thenReturn(true);
        RuntimeException boom = new RuntimeException("đã gửi rồi nhưng bước sau lỗi");
        org.mockito.Mockito.doThrow(boom).when(emailChannel).send(any());
        when(emailChannel.isRetryable(boom)).thenReturn(false);

        assertThatThrownBy(() -> dispatcher.dispatch(requestFor(ChannelType.EMAIL)))
                .isInstanceOf(ChannelDeliveryException.class);

        verify(dedupStore, never()).release("evt-1:EMAIL");
    }

    @Test
    void kenh_chua_co_impl_bi_bo_qua_khong_ram_loi() {
        dispatcher.dispatch(requestFor(ChannelType.WEBSOCKET));

        verify(emailChannel, never()).send(any());
        verify(inAppChannel, never()).send(any());
        // Kênh không có bean phải bị loại TRƯỚC khi chạm tới Redis: nếu giành khoá rồi mới phát
        // hiện không gửi được thì khoá đó nằm lại 24h và chặn luôn lần triển khai kênh thật sau này.
        verify(dedupStore, never()).acquireOnce(anyString(), any(Duration.class));
    }
}
