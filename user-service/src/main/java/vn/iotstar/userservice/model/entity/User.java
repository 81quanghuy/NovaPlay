package vn.iotstar.userservice.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Gender;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = USER_TABLE_NAME)
public class User extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = Constants.USER_ID_COLUMN, unique = true, nullable = false)
    private String userId;

    @Column(name = Constants.ACCOUNT_ID_COLUMN, unique = true, nullable = false)
    private String accountId;

    @Column(name = USER_NAME_COLUMN)
    private String username;

    @Column(name = AVATAR_COLUMN)
    private String avatar;

    @Column(name = ADDRESS_COLUMN)
    private String address;

    @Column(name= DOB_COLUMN)
    private Date dayOfBirth;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = GENDER_COLUMN)
    private Gender gender;

    @Column(name=IS_ONLINE_COLUMN)
    @Builder.Default
    private Boolean isOnline = false;
}

