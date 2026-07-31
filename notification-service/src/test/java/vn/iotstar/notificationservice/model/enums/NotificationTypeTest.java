package vn.iotstar.notificationservice.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @Test
    void phan_giai_khong_phan_biet_hoa_thuong_va_khoang_trang() {
        assertThat(NotificationType.from(" otp ")).isEqualTo(NotificationType.OTP);
        assertThat(NotificationType.from("ACCOUNT_ACTIVATED")).isEqualTo(NotificationType.ACCOUNT_ACTIVATED);
    }

    @Test
    @DisplayName("loại lạ rơi về GENERIC — bên phát thêm loại mới không được làm chết consumer")
    void loai_la_ve_generic() {
        assertThat(NotificationType.from("SOMETHING_NEW")).isEqualTo(NotificationType.GENERIC);
        assertThat(NotificationType.from(null)).isEqualTo(NotificationType.GENERIC);
        assertThat(NotificationType.from("  ")).isEqualTo(NotificationType.GENERIC);
    }

    @Test
    void khoa_i18n_duoc_sinh_theo_ten_hang_so() {
        assertThat(NotificationType.ACCOUNT_ACTIVATED.titleKey())
                .isEqualTo("notification.account-activated.title");
        assertThat(NotificationType.ACCOUNT_ACTIVATED.bodyKey())
                .isEqualTo("notification.account-activated.body");
    }
}
