package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.utils.AbstractBaseEntity;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.authservice.util.RoleName;

import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = Constants.ROLE_TABLE)
public class Role extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ROLE_ID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = Constants.ROLE_NAME)
    private RoleName roleName;

    @Builder.Default
    @Column(name = Constants.ROLE_DESCRIPTION)
    private String description = "";

}
