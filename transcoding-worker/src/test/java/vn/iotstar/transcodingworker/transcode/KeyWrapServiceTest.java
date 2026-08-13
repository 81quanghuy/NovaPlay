package vn.iotstar.transcodingworker.transcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class KeyWrapServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("wrap() không bao giờ trả về chính khoá thô (base64) trong ciphertext")
    void wrapNeverLeaksRawKey() {
        KeyWrapService service = new KeyWrapService(SECRET);
        byte[] rawKey = new byte[16];
        new SecureRandom().nextBytes(rawKey);

        String wrapped = service.wrap(rawKey);

        assertThat(wrapped).isNotEqualTo(Base64.getEncoder().encodeToString(rawKey));
        assertThat(Base64.getDecoder().decode(wrapped)).hasSizeGreaterThan(rawKey.length);
    }

    @Test
    @DisplayName("hai lần wrap cùng một khoá cho ra ciphertext khác nhau (nonce ngẫu nhiên mỗi lần)")
    void wrapIsNonDeterministic() {
        KeyWrapService service = new KeyWrapService(SECRET);
        byte[] rawKey = new byte[16];

        String first = service.wrap(rawKey);
        String second = service.wrap(rawKey);

        assertThat(first).isNotEqualTo(second);
    }
}
