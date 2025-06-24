package vn.iotstar.authservice.model.entity;


import jakarta.persistence.*;
import lombok.*;

import vn.iostar.utils.AbstractBaseEntity;
import vn.iotstar.authservice.util.Constants;
import vn.iotstar.authservice.util.TokenType;

import java.io.Serializable;
import java.util.Date;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = Constants.TOKEN_TABLE)
public class Token extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.TOKEN_ID)
    private String id;

    @Column(name = Constants.TOKEN, unique = true, length = 700)
    private String tokenValue;

    @Column(name = Constants.ACCOUNT_ID)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = Constants.TOKEN_TYPE)
    private TokenType type;

    @Builder.Default
    @Column(name = Constants.IS_REVOKED)
    private Boolean isRevoked = false;

    @Column(name = Constants.ISSUED_AT)
    private Date issuedAt;

    @Column(name = Constants.EXPIRED_AT)
    private Date expiredAt;

    @Column(name = Constants.IP_ADDRESS)
    private String ipAddress;
}
