package vn.iotstar.userservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.userservice.util.AuditableDocument;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Plan;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static vn.iotstar.userservice.util.Constants.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = USER_PROFILE_TABLE_NAME)
@TypeAlias("UserProfile")
@CompoundIndex(name = "email_active_idx", def = "{'email': 1, 'active': 1}")
public class UserProfile extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(Constants.USER_ID_COLUMN)
    private String id;

    @Indexed(unique = true)
    @Field(EMAIL_COLUMN)
    private String email;

    @Field(PREFERRED_USERNAME)
    private String preferredUsername;

    @Field(USER_NAME_COLUMN)
    private String displayName;

    @Field(AVATAR_COLUMN)
    private String avatarUrl;

    @Field(LOCATE_COLUMN)
    private String locale;

    @Field(PLAN_COLUMN)
    private Plan plan;

    @Indexed
    @Field(IS_ACTIVE_COLUMN)
    private boolean active = true;

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
            locale = locale.replace('_','-').trim();
            try {
                var tag = Locale.forLanguageTag(locale).toLanguageTag();
                if (!tag.isEmpty()) locale = tag; // ví dụ: "vi-VN"
            } catch (Exception ignored) {
                throw new IllegalArgumentException("Invalid locale format: " + locale);
            }
        }
        if (plan != null) plan = Plan.MEMBER;

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
