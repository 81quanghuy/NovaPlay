package vn.iotstar.emailservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.emailservice.util.Constants;

import java.io.Serial;
import java.io.Serializable;

@Document(collection = Constants.EMAIL_LOGGING_TABLE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogMQ implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field(Constants.EMAIL_LOGGING_COLUMN_ACTION)
    private String action;

    @Field(Constants.EMAIL_LOGGING_COLUMN_MESSAGE)
    private String message;

    @Field(Constants.EMAIL_LOGGING_COLUMN_STATUS)
    private String status;

    @Field(Constants.EMAIL_LOGGING_COLUMN_RECIPIENT)
    private String recipient;

    @Field(Constants.EMAIL_LOGGING_KAFKA_TOPIC)
    private String topic;

    @Field(Constants.EMAIL_LOGGING_KAFKA_STATUS)
    private String kafkaStatus;

    @Field(Constants.EMAIL_LOGGING_COLUMN_TIMESTAMP)
    private Long timestamp;
}
