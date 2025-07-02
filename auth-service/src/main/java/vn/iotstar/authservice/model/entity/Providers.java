package vn.iotstar.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import vn.iotstar.utils.AbstractBaseEntity;
import vn.iotstar.authservice.util.Constants;


import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = Constants.PROVIDERS_TABLE)
public class Providers extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.PROVIDER_ID)
    private String id;

    @Column(name = Constants.PROVIDER_NAME)
    private String providerName;

    @Column(name = Constants.USER_ID)
    private String userId;

    @Column(name = Constants.PROVIDER_URL)
    private String providerUrl;
}
