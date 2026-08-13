package vn.iotstar.streamingservice.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.iotstar.streamingservice.exception.UnauthorizedPlaybackException;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackTokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private PlaybackTokenService service(long ttlHours) {
        return new PlaybackTokenService(SECRET, ttlHours);
    }

    @Test
    @DisplayName("token hợp lệ cho đúng manifestId thì validate thành công")
    void validTokenPassesForMatchingManifest() {
        PlaybackTokenService service = service(4);
        String token = service.mint("user@novaplay.vn", "manifest-1");

        Claims claims = service.validate(token, "manifest-1");

        assertThat(claims.getSubject()).isEqualTo("user@novaplay.vn");
    }

    @Test
    @DisplayName("token của manifest khác bị từ chối — không xem chéo được video khác")
    void tokenRejectedForDifferentManifest() {
        PlaybackTokenService service = service(4);
        String token = service.mint("user@novaplay.vn", "manifest-1");

        assertThatThrownBy(() -> service.validate(token, "manifest-2"))
                .isInstanceOf(UnauthorizedPlaybackException.class);
    }

    @Test
    @DisplayName("token hết hạn bị từ chối")
    void expiredTokenRejected() throws InterruptedException {
        // ttl 0 giờ vẫn có exp = now, nhưng cần một khoảng trễ thật để chắc chắn đã qua exp.
        PlaybackTokenService service = new PlaybackTokenService(SECRET, 0);
        String token = service.mint("user@novaplay.vn", "manifest-1");
        Thread.sleep(1100);

        assertThatThrownBy(() -> service.validate(token, "manifest-1"))
                .isInstanceOf(UnauthorizedPlaybackException.class);
    }

    @Test
    @DisplayName("token rỗng/null bị từ chối")
    void blankTokenRejected() {
        PlaybackTokenService service = service(4);

        assertThatThrownBy(() -> service.validate("", "manifest-1"))
                .isInstanceOf(UnauthorizedPlaybackException.class);
        assertThatThrownBy(() -> service.validate(null, "manifest-1"))
                .isInstanceOf(UnauthorizedPlaybackException.class);
    }

    @Test
    @DisplayName("remainingValidity() nằm trong khoảng (0, ttl] cho token còn hiệu lực")
    void remainingValidityWithinTtl() {
        PlaybackTokenService service = service(4);
        String token = service.mint("user@novaplay.vn", "manifest-1");
        Claims claims = service.validate(token, "manifest-1");

        Duration remaining = service.remainingValidity(claims);

        assertThat(remaining.isNegative()).isFalse();
        assertThat(remaining).isLessThanOrEqualTo(Duration.ofHours(4));
    }
}
