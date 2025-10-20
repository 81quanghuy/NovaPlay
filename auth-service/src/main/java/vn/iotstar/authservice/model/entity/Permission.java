package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = Constants.PERMISSIONS_TABLE,
        uniqueConstraints = {
                @UniqueConstraint(name = Constants.UK_PERMISSION_NAME, columnNames = Constants.PERMISSION_NAME)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permission extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = Constants.PERMISSION_ID)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = Constants.PERMISSION_NAME,unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = Constants.PERMISSION_DESCRIPTION, length = 255)
    private String description;
}
