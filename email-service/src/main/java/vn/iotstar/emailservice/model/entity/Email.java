package vn.iotstar.emailservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.emailservice.util.AuditableDocument;
import vn.iotstar.emailservice.util.Constants;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Document(collection = Constants.EMAIL_TABLE)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Email extends AuditableDocument implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field(Constants.EMAIL_COLUMN_RECIPIENT)
    @Indexed(name = Constants.ID_EMAIL_RECIPIENT)
    private String recipientEmail;

    @Field(Constants.EMAIL_COLUMN_OTP)
    private String otp;

    @Indexed(name = Constants.IDX_EMAIL_EXPIRATION_TIME, expireAfter = "0s")
    @Field(Constants.EMAIL_COLUMN_EXPIRATION_TIME)
    private Instant expirationTime;

    @Field(Constants.EMAIL_COLUMN_IS_VERIFIED)
    private Boolean isVerified = false;
}
