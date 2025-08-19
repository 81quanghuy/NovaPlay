package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Constants.ROLE_TABLE,
        uniqueConstraints = {
                @UniqueConstraint(name = Constants.UK_ROLE_ROLE_NAME, columnNames = Constants.ROLE_NAME)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ROLE_ID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = Constants.ROLE_NAME)
    private RoleName roleName;

    @Builder.Default
    @Column(name = Constants.ROLE_DESCRIPTION)
    private String description = "";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = Constants.ROLE_PERMISSIONS_TABLE,
            joinColumns = @JoinColumn(name = Constants.ROLE_ID),
            inverseJoinColumns = @JoinColumn(name = Constants.PERMISSION_ID)
    )
    private Set<Permission> permissions = new HashSet<>();
}
