package vn.iotstar.userservice.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileNormalizeTest {

    private static UserProfile withLocale(String locale) {
        return UserProfile.builder().email("a@b.com").locale(locale).build();
    }

    @Test
    @DisplayName("email được trim và hạ chữ thường")
    void normalisesEmail() {
        UserProfile p = UserProfile.builder().email("  User@Example.COM  ").build();

        p.normalize();

        assertThat(p.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("sinh id khi chưa có")
    void generatesIdWhenMissing() {
        UserProfile p = UserProfile.builder().email("a@b.com").build();

        p.normalize();

        assertThat(p.getId()).isNotBlank();
    }

    @Test
    @DisplayName("giữ nguyên id đã có")
    void keepsExistingId() {
        UserProfile p = UserProfile.builder().id("fixed-id").email("a@b.com").build();

        p.normalize();

        assertThat(p.getId()).isEqualTo("fixed-id");
    }

    @Test
    @DisplayName("mặc định plan là MEMBER")
    void defaultsPlanToMember() {
        UserProfile p = UserProfile.builder().email("a@b.com").build();

        p.normalize();

        assertThat(p.getPlan()).isEqualTo(Plan.MEMBER);
    }

    @Test
    @DisplayName("profile tạo bằng builder mặc định là active")
    void isActiveByDefaultWhenBuiltWithBuilder() {
        // Không có @Builder.Default thì Lombok bỏ qua giá trị khởi tạo của field và mọi user
        // đăng ký qua Kafka đều bị tạo ra ở trạng thái vô hiệu hoá.
        UserProfile p = UserProfile.builder().email("a@b.com").build();

        assertThat(p.isActive()).isTrue();
        assertThat(p.isMarketingOptIn()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("locale được chuẩn hoá về dạng BCP-47")
    @CsvSource({
            "vi-VN, vi-VN",
            "vi_VN, vi-VN",
            "en_us, en-US",
            "'  fr-FR  ', fr-FR"
    })
    void normalisesLocaleToBcp47(String input, String expected) {
        UserProfile p = withLocale(input);

        p.normalize();

        assertThat(p.getLocale()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "locale rác: \"{0}\"")
    @DisplayName("locale không hợp lệ bị từ chối thay vì lọt qua im lặng")
    @ValueSource(strings = {"!!!", "@@", "123456789012345678"})
    void rejectsInvalidLocale(String invalid) {
        UserProfile p = withLocale(invalid);

        // Locale.forLanguageTag không bao giờ ném exception — nó trả về tag rỗng hoặc "und",
        // nên phải kiểm tra kết quả tường minh chứ không thể dựa vào try/catch.
        assertThatThrownBy(p::normalize)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid locale");
    }

    @Test
    @DisplayName("locale rỗng trở thành null thay vì gây lỗi")
    void blankLocaleBecomesNull() {
        UserProfile p = withLocale("   ");

        p.normalize();

        assertThat(p.getLocale()).isNull();
    }
}
