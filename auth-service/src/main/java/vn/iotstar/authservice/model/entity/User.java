package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.utils.AbstractBaseEntity;


import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Constants.USER_TABLE,
        uniqueConstraints = {
                @UniqueConstraint(name =Constants.UK_USER_EMAIL, columnNames = Constants.USER_COLUMN_EMAIL),
                @UniqueConstraint(name = Constants.UK_USER_USERNAME, columnNames = Constants.USER_COLUMN_USERNAME)
        },
        indexes = {
                @Index(name = Constants.IDX_USER_IS_ACTIVE, columnList = Constants.USER_COLUMN_IS_ACTIVE)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.USER_ID)
    private UUID id;

    @Column(name = Constants.USER_COLUMN_USERNAME)
    private String username;

    @Column(name = Constants.USER_COLUMN_EMAIL, unique = true)
    private String email;

    @Column(name = Constants.USER_COLUMN_PASSWORD)
    private String password;

    @Column(name = Constants.USER_COLUMN_IS_ACTIVE)
    private Boolean isActive;

    @Column(name = Constants.USER_COLUMN_IS_EMAIL_VERIFIED, nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = Constants.USER_COLUMN_LAST_LOGIN_AT)
    private Date lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = Constants.USER_ROLE_TABLE,
            joinColumns = @JoinColumn(name = Constants.USER_ID),
            inverseJoinColumns = @JoinColumn(name = Constants.ROLE_ID)
    )
    private Set<Role> roles;

    @OneToMany(mappedBy = Constants.PROVIDERS_COLUMN_USER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Provider> providers;
}
