package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

import vn.iostar.utils.AbstractBaseEntity;
import vn.iotstar.authservice.util.Constants;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = Constants.ACCOUNT_TABLE)
public class Account extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ACCOUNT_ID)
    private String id;

    @Column(name = Constants.USER_ID)
    private String userId;

    @Email
    @Column(name = Constants.EMAIL, unique = true)
    private String email;

    @Column(name = Constants.PASSWORD)
    private String password;

    // Xác định xem tài khoản đã được xác minh hay chưa
    @Column(name = Constants.IS_ACTIVE)
    private Boolean isActive;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = Constants.ACCOUNT_ROLE,
            joinColumns = @JoinColumn(name = Constants.ACCOUNT_ID),
            inverseJoinColumns = @JoinColumn(name = Constants.ROLE_ID)
    )
    private Set<Role> roles;
}
