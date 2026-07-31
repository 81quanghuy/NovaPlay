package vn.iotstar.notificationservice.consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import vn.iotstar.notificationservice.channel.ChannelRoutingPolicy;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.dispatch.NotificationDispatcher;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.AuditLogger;
import vn.iotstar.utils.dto.EmailOtpRequested;
import vn.iotstar.utils.dto.NotificationRequested;
import vn.iotstar.utils.dto.UserRegister;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private Acknowledgment ack;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(dispatcher, new ChannelRoutingPolicy(),
                new NotificationMetrics(new SimpleMeterRegistry()), new AuditLogger());
    }

    private NotificationRequest captureDispatched() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    // ---- send-email.v1 ----

    @Test
    void otp_duoc_dich_sang_request_chuan_hoa() {
        var evt = new EmailOtpRequested("msg-1", "u-1", "user@novaplay.dev",
                Map.of("otp", "123456", "expireMinutes", "5", "locale", "en"));

        consumer.handleEmailOtp(evt, "send-email.v1", 0, 42L, ack);

        NotificationRequest request = captureDispatched();
        assertThat(request.dedupKey()).isEqualTo("msg-1");
        assertThat(request.type()).isEqualTo(NotificationType.OTP);
        assertThat(request.channels()).containsExactly(ChannelType.EMAIL);
        assertThat(request.locale()).isEqualTo(Locale.forLanguageTag("en"));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("thiếu messageId thì rơi về toạ độ bản ghi, ổn định qua các lần nhận lại")
    void thieu_message_id_thi_dung_toa_do_ban_ghi() {
        var evt = new EmailOtpRequested(null, "u-1", "user@novaplay.dev", Map.of("otp", "123456"));

        consumer.handleEmailOtp(evt, "send-email.v1", 2, 99L, ack);

        // Trước khi gộp, nhánh này sinh khoá theo System.currentTimeMillis() nên mỗi lần nhận lại
        // lại tạo khoá mới và cơ chế chống trùng trở thành vô tác dụng.
        assertThat(captureDispatched().dedupKey()).isEqualTo("send-email.v1:2:99");
    }

    @Test
    void payload_hong_bi_tu_choi_ngay_va_khong_dispatch() {
        assertThatThrownBy(() -> consumer.handleEmailOtp(null, "send-email.v1", 0, 1L, ack))
                .isInstanceOf(IllegalArgumentException.class);

        var thieuOtp = new EmailOtpRequested("m", "u", "user@novaplay.dev", Map.of());
        assertThatThrownBy(() -> consumer.handleEmailOtp(thieuOtp, "send-email.v1", 0, 1L, ack))
                .isInstanceOf(IllegalArgumentException.class);

        var thieuEmail = new EmailOtpRequested("m", "u", "  ", Map.of("otp", "1"));
        assertThatThrownBy(() -> consumer.handleEmailOtp(thieuEmail, "send-email.v1", 0, 1L, ack))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
        verify(ack, never()).acknowledge();
    }

    // ---- activate-account.v1 ----

    @Test
    @DisplayName("kích hoạt tài khoản dùng khoá nghiệp vụ theo email, không dùng toạ độ bản ghi")
    void kich_hoat_dung_khoa_nghiep_vu() {
        var evt = new UserRegister("huy", "user@novaplay.dev");

        consumer.handleUserRegister(evt, "activate-account.v1", 1, 7L, ack);

        // UserRegister không có messageId, và outbox của auth-service có thể publish lại vào một
        // offset khác — dùng toạ độ bản ghi sẽ khiến thư chào mừng bị gửi hai lần.
        NotificationRequest request = captureDispatched();
        assertThat(request.dedupKey()).isEqualTo("activate-account:user@novaplay.dev");
        assertThat(request.channels())
                .containsExactlyInAnyOrder(ChannelType.EMAIL, ChannelType.IN_APP);
        assertThat(request.variables()).containsEntry("username", "huy");
    }

    @Test
    void kich_hoat_thieu_email_bi_tu_choi() {
        assertThatThrownBy(() ->
                consumer.handleUserRegister(new UserRegister("huy", null), "activate-account.v1", 0, 1L, ack))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- notification.requested.v1 ----

    @Test
    void topic_chung_ton_trong_kenh_do_producer_chi_dinh() {
        var evt = new NotificationRequested("msg-9", "u-9", "user@novaplay.dev",
                "GENERIC", Set.of("EMAIL"), "vi-VN", Map.of("title", "Xin chào"));

        consumer.handleNotificationRequested(evt, "notification.requested.v1", 0, 3L, ack);

        NotificationRequest request = captureDispatched();
        assertThat(request.channels()).containsExactly(ChannelType.EMAIL);
        assertThat(request.type()).isEqualTo(NotificationType.GENERIC);
    }

    @Test
    @DisplayName("loại thông báo lạ rơi về GENERIC chứ không làm chết consumer")
    void loai_la_ve_generic() {
        var evt = new NotificationRequested("msg-9", null, "user@novaplay.dev",
                "PAYMENT_SETTLED", null, null, Map.of());

        consumer.handleNotificationRequested(evt, "notification.requested.v1", 0, 3L, ack);

        assertThat(captureDispatched().type()).isEqualTo(NotificationType.GENERIC);
    }
}
