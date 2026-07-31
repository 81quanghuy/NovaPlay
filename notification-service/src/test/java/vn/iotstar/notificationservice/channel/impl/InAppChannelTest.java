package vn.iotstar.notificationservice.channel.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.entity.Notification;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.repository.NotificationRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppChannelTest {

    @Mock private NotificationRepository repository;

    private InAppChannel channel;

    @BeforeEach
    void setUp() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        channel = new InAppChannel(repository, messageSource);
    }

    private static NotificationRequest request() {
        return NotificationRequest.builder()
                .dedupKey("evt-1")
                .messageId("msg-1")
                .userId("u-1")
                .userEmail("user@novaplay.dev")
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .locale(Locale.forLanguageTag("vi-VN"))
                .variables(Map.of("username", "huy"))
                .build();
    }

    @Test
    @DisplayName("_id ghép từ dedupKey và kênh — đây chính là cơ chế chống trùng")
    void id_duoc_ghep_tu_dedup_key() {
        channel.send(request());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("evt-1:IN_APP");
    }

    @Test
    void noi_dung_duoc_render_theo_locale() {
        channel.send(request());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Tài khoản đã được kích hoạt");
        assertThat(saved.getBody()).isEqualTo("Chào mừng huy đến với NovaPlay!");
        assertThat(saved.getUserEmail()).isEqualTo("user@novaplay.dev");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("GENERIC dùng title/body của producer, không đè bằng bundle i18n")
    void generic_uu_tien_noi_dung_cua_producer() {
        NotificationRequest generic = NotificationRequest.builder()
                .dedupKey("evt-2")
                .userEmail("user@novaplay.dev")
                .type(NotificationType.GENERIC)
                .locale(Locale.forLanguageTag("vi-VN"))
                .variables(Map.of("title", "Thanh toán thành công", "body", "Đơn #123 đã được xử lý"))
                .build();

        channel.send(generic);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        // Bundle chỉ có chuỗi giữ chỗ cho GENERIC; chỉ producer mới biết nội dung thật.
        assertThat(captor.getValue().getTitle()).isEqualTo("Thanh toán thành công");
        assertThat(captor.getValue().getBody()).isEqualTo("Đơn #123 đã được xử lý");
    }

    @Test
    @DisplayName("loại cố định vẫn dùng bundle, không để producer ghi đè")
    void loai_co_dinh_van_dung_bundle() {
        channel.send(request());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Tài khoản đã được kích hoạt");
    }

    @Test
    @DisplayName("chèn trùng được coi là đã gửi thành công, không phải lỗi")
    void chen_trung_khong_phai_loi() {
        doThrow(new DuplicateKeyException("_id trùng")).when(repository).insert(any(Notification.class));

        assertThatCode(() -> channel.send(request())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mongo hỏng thì ném ra để dispatcher xử lý retry")
    void mongo_hong_thi_nem_ra() {
        doThrow(new DataAccessResourceFailureException("mongo down"))
                .when(repository).insert(any(Notification.class));

        assertThatThrownBy(() -> channel.send(request()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void kenh_khai_bao_dung_loai() {
        assertThat(channel.type()).isEqualTo(ChannelType.IN_APP);
    }
}
