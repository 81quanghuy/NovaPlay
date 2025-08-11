package vn.iotstar.userservice.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Gender;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@Table(
        name = USER_TABLE_NAME,
        uniqueConstraints = {
                @UniqueConstraint(name = UK_USER_PROFILE_EMAIL, columnNames = EMAIL_COLUMN)
        },
        indexes = {
                @Index(name = IDX_PROFILE_ACTIVE, columnList = IS_ACTIVE_COLUMN)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = Constants.USER_ID_COLUMN, unique = true, nullable = false,columnDefinition = UUID_CONST)
    private UUID userId;

    @Column(name = EMAIL_COLUMN, nullable = false, length = 255)
    private String email;

    @Column(name = PREFERRED_USERNAME, length = 100)
    private String preferredUsername;

    @Column(name = USER_NAME_COLUMN, length = 150)
    private String displayName;

    @Column(name = AVATAR_COLUMN, length = 512)
    private String avatarUrl;

    @Column(name = LOCATE_COLUMN, length = 16)
    private String locale;

    @Column(name = PLAN_COLUMN, length = 50)
    private String plan;

    @Column(name = IS_ACTIVE_COLUMN, nullable = false)
    private boolean active = true;

    @Column(name = MARKETING_OPT_IN_COLUMN, nullable = false)
    private boolean marketingOptIn = false;

    @Column(name = MARKETING_OPT_IN_AT_COLUMN)
    private Instant marketingOptInAt;

    @PreUpdate
    @PrePersist
    void normalize() {
        if (email != null) email = email.trim().toLowerCase();
        if (preferredUsername != null) preferredUsername = preferredUsername.trim();
        if (displayName != null) displayName = displayName.trim();
        if (avatarUrl != null) avatarUrl = avatarUrl.trim();
        if (locale != null) {
            locale = locale.replace('_','-').trim();
            try {
                var tag = Locale.forLanguageTag(locale).toLanguageTag();
                if (!tag.isEmpty()) locale = tag; // chuẩn hoá như "vi-VN"
            } catch (Exception ignored) {
                throw new IllegalArgumentException("Invalid locale format: " + locale);
            }
        }
        if (plan != null) plan = plan.trim();
    }
}

