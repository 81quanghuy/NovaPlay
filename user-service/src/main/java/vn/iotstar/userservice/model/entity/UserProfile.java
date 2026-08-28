package vn.iotstar.userservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.userservice.common.audit.AuditableDocument;
import vn.iotstar.userservice.exception.BadRequestException;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static vn.iotstar.userservice.util.Constants.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = USER_PROFILE_TABLE_NAME)
@TypeAlias("UserProfile")
public class UserProfile extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(Constants.USER_ID_COLUMN)
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    @Field(EMAIL_COLUMN)
    private String email;

    @Field(PREFERRED_USERNAME)
    private String preferredUsername;

    @Field(USER_NAME_COLUMN)
    private String displayName;

    @Field("phoneNumber")
    private String phoneNumber;

    @Field("bio")
    private String bio;

    @Field(AVATAR_COLUMN)
    private String avatarUrl;

    @Field(LOCATE_COLUMN)
    private String locale;

    @Field(PLAN_COLUMN)
    private Plan plan;

    // @Builder.Default là bắt buộc: không có nó, Lombok bỏ qua giá trị khởi tạo và builder
    // tạo ra profile với active=false, khiến mọi user đăng ký qua Kafka đều bị vô hiệu hoá.
    @Builder.Default
    @Field(IS_ACTIVE_COLUMN)
    private boolean active = true;

    @Builder.Default
    @Field(MARKETING_OPT_IN_COLUMN)
    private boolean marketingOptIn = false;

    @Field(MARKETING_OPT_IN_AT_COLUMN)
    private Instant marketingOptInAt;

    public void normalize() {
        if (email != null) email = email.trim().toLowerCase();
        if (preferredUsername != null) preferredUsername = preferredUsername.trim();
        if (displayName != null) displayName = displayName.trim();
        if (avatarUrl != null) avatarUrl = avatarUrl.trim();
        if (locale != null) {
            locale = normalizeLocale(locale);
        }
        if (plan == null) plan = Plan.MEMBER;

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    /**
     * Chuẩn hoá locale về dạng BCP-47 (ví dụ "vi_vn" -> "vi-VN").
     * <p>
     * {@link Locale#forLanguageTag} không bao giờ ném exception: với đầu vào rác nó trả về
     * locale rỗng, còn với tag không nhận dạng được thì {@code toLanguageTag()} trả "und"
     * (undetermined). Vì vậy phải kiểm tra kết quả tường minh thay vì bọc try/catch.
     */
    private static String normalizeLocale(String raw) {
        String cleaned = raw.replace('_', '-').trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String tag = Locale.forLanguageTag(cleaned).toLanguageTag();
        if (tag.isEmpty() || "und".equals(tag)) {
            throw new BadRequestException("Invalid locale format: " + raw);
        }
        return tag;
    }
}
