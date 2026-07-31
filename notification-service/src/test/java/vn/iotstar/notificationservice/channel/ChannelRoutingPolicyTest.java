package vn.iotstar.notificationservice.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRoutingPolicyTest {

    private final ChannelRoutingPolicy policy = new ChannelRoutingPolicy();

    @Test
    @DisplayName("OTP chỉ đi qua email, không bao giờ vào in-app")
    void otp_khong_vao_in_app() {
        // Đặt mã OTP vào danh sách đọc lại được sẽ phá vỡ tính "một lần" của nó.
        assertThat(policy.channelsFor(NotificationType.OTP))
                .containsExactly(ChannelType.EMAIL);
    }

    @Test
    void kich_hoat_tai_khoan_di_ca_email_lan_in_app() {
        assertThat(policy.channelsFor(NotificationType.ACCOUNT_ACTIVATED))
                .containsExactlyInAnyOrder(ChannelType.EMAIL, ChannelType.IN_APP);
    }

    @Test
    void kenh_do_producer_chi_dinh_duoc_ton_trong() {
        assertThat(policy.resolve(NotificationType.GENERIC, Set.of("email", "IN_APP")))
                .containsExactlyInAnyOrder(ChannelType.EMAIL, ChannelType.IN_APP);
    }

    @Test
    @DisplayName("danh sách kênh rỗng hoặc null rơi về chính sách mặc định")
    void rong_thi_ve_mac_dinh() {
        assertThat(policy.resolve(NotificationType.OTP, null))
                .containsExactly(ChannelType.EMAIL);
        assertThat(policy.resolve(NotificationType.OTP, Set.of()))
                .containsExactly(ChannelType.EMAIL);
    }

    @Test
    @DisplayName("tên kênh sai chính tả bị bỏ qua chứ không làm chết consumer")
    void ten_kenh_sai_bi_bo_qua() {
        assertThat(policy.resolve(NotificationType.GENERIC, Set.of("EMAIL", "smoke-signal")))
                .containsExactly(ChannelType.EMAIL);
    }

    @Test
    @DisplayName("nếu lọc xong không còn kênh hợp lệ nào thì vẫn về mặc định, thông báo không biến mất")
    void toan_bo_ten_sai_thi_ve_mac_dinh() {
        assertThat(policy.resolve(NotificationType.ACCOUNT_ACTIVATED, Set.of("carrier-pigeon")))
                .containsExactlyInAnyOrder(ChannelType.EMAIL, ChannelType.IN_APP);
    }
}
