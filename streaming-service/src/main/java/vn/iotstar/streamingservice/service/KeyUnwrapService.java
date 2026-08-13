package vn.iotstar.streamingservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.exception.BadRequestException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Mở khoá AES-128 mã hoá segment HLS — nghịch đảo của transcoding-worker's {@code KeyWrapService}.
 * Dùng CHUNG bí mật {@code TRANSCODE_KEY_WRAP_SECRET} với worker (một credential chia sẻ giữa hai
 * service, không phải code dùng chung). Khoá thô sau khi mở KHÔNG bao giờ được cache/log — chỉ tồn
 * tại trong bộ nhớ đủ lâu để trả về response của endpoint phát khoá.
 */
@Component
public class KeyUnwrapService {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final SecretKeySpec wrapKey;

    public KeyUnwrapService(@Value("${transcode.key-wrap-secret}") String base64Secret) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.wrapKey = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] unwrap(String wrappedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(wrappedBase64);
            if (combined.length <= NONCE_BYTES) {
                throw new BadRequestException("Malformed wrapped key.");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(combined, NONCE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to unwrap HLS encryption key", e);
        }
    }
}
