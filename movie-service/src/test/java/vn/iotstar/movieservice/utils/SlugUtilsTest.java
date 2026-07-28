package vn.iotstar.movieservice.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "Nhà Bà Nữ,                nha-ba-nu",
            "Người Nhện,               nguoi-nhen",
            "Đất Rừng Phương Nam,      dat-rung-phuong-nam",
            "The Dark Knight,          the-dark-knight",
            "Spider-Man: No Way Home,  spider-man-no-way-home",
            "Avengers   Endgame,       avengers-endgame",
            "2001: A Space Odyssey,    2001-a-space-odyssey"
    })
    @DisplayName("bóc dấu tiếng Việt và chuẩn hoá về dạng dùng được cho URL")
    void producesUrlSafeSlug(String input, String expected) {
        assertThat(SlugUtils.toSlug(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("chữ Đ được thay thủ công vì NFD không tách được dấu gạch ngang của nó")
    void handlesDStroke() {
        assertThat(SlugUtils.toSlug("Đông Dương")).isEqualTo("dong-duong");
        assertThat(SlugUtils.toSlug("đêm")).isEqualTo("dem");
    }

    @Test
    @DisplayName("không để lại dấu gạch thừa ở hai đầu")
    void trimsEdgeDashes() {
        assertThat(SlugUtils.toSlug("!!! Phim Hay !!!")).isEqualTo("phim-hay");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "!!!", "***", "。。。"})
    @DisplayName("trả null khi không còn ký tự nào dùng được, để tầng gọi báo lỗi tường minh")
    void returnsNullWhenNothingUsableRemains(String input) {
        assertThat(SlugUtils.toSlug(input)).isNull();
    }
}
