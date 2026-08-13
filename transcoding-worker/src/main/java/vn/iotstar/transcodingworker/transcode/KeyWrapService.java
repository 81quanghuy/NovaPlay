package vn.iotstar.transcodingworker.transcode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bọc khoá AES-128 dùng để mã hoá segment HLS bằng {@code TRANSCODE_KEY_WRAP_SECRET} — bí mật
 * chia sẻ với streaming-service (KHÔNG phải shared code, chỉ là một credential như
 * {@code GATEWAY_SHARED_SECRET} đã có). Khoá thô KHÔNG BAO GIỜ rời khỏi bộ nhớ của worker/
 * streaming-service — chỉ ciphertext (AES-GCM, base64) được lưu vào {@code VideoManifest}.
 */
@Component
public class KeyWrapService {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final SecretKeySpec wrapKey;

    public KeyWrapService(@Value("${transcode.key-wrap-secret}") String base64Secret) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.wrapKey = new SecretKeySpec(keyBytes, "AES");
    }

    /** @return base64(nonce || ciphertext+tag) */
    public String wrap(byte[] rawKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(rawKey);

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to wrap HLS encryption key", e);
        }
    }
}
