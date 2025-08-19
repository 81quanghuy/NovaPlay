package vn.iotstar.authservice.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = Constants.PROVIDERS_TABLE,
        uniqueConstraints = {
                @UniqueConstraint(name = Constants.UK_PROVIDER_NAME_AND_USER_ID,
                        columnNames = {Constants.PROVIDER_NAME, Constants.PROVIDER_COLUMN_USER_ID}),
                @UniqueConstraint(name = Constants.UK_PROVIDER_USER_AND_NAME,
                        columnNames = {Constants.USER_ID, Constants.PROVIDER_NAME})
        },
        indexes = {
                @Index(name = Constants.IDX_PROVIDER_USER_ID, columnList = Constants.USER_ID)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Provider extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.PROVIDER_ID)
    private String id;

    @Column(name = Constants.PROVIDER_NAME)
    private String providerName;

    @Column(name = Constants.PROVIDER_COLUMN_USER_ID)
    private String userId;

    @Column(name = Constants.PROVIDER_URL)
    private String providerUrl;

    // --- Relationships ---

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.USER_ID, nullable = false)
    private User user;
}
