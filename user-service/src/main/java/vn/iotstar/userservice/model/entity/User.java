package vn.iotstar.userservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import vn.iotstar.utils.AbstractBaseEntity;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Gender;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

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

    @Column(name = USER_NAME_COLUMN, columnDefinition = "nvarchar(255)")
    private String username;

    @Column(name = AVATAR_COLUMN, columnDefinition = "nvarchar(255)")
    private String avatar;

    @Column(name = ADDRESS_COLUMN, columnDefinition = "nvarchar(255)")
    private String address;

    @Column(name= DOB_COLUMN, columnDefinition = "date")
    private LocalDate dayOfBirth;

    @Column(name = GENDER_COLUMN)
    private Gender gender;

    @Column(name=IS_ONLINE_COLUMN, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isOnline = false;
}

