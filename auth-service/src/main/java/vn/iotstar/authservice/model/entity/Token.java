package vn.iotstar.authservice.model.entity;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.authservice.util.TokenType;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = Constants.TOKEN_TABLE,
        uniqueConstraints = {
                @UniqueConstraint(name = Constants.UK_TOKEN_VALUE, columnNames = Constants.TOKEN)
        },
        indexes = {
                @Index(name = Constants.IDX_TOKEN_USER_ID, columnList = Constants.USER_ID),
                @Index(name = Constants.IDX_TOKEN_IS_REVOKED, columnList = Constants.IS_REVOKED)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Token extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.TOKEN_ID)
    private UUID id;

    @Column(name = Constants.TOKEN, unique = true, length = 700)
    private String tokenValue;

    @Enumerated(EnumType.STRING)
    @Column(name = Constants.TOKEN_TYPE)
    private TokenType type;

    @Builder.Default
    @Column(name = Constants.IS_REVOKED)
    private Boolean isRevoked = false;

    @Column(name = Constants.EXPIRED_AT)
    private Instant expiredAt;

    // --- Relationships ---

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = Constants.TOKEN_COLUMN_USER_ID, nullable = false)
    private User user;
}
