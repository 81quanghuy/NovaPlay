package vn.iotstar.emailservice.util;

/**
 * Constants class to hold all the constant values used in the application.
 * This class is not meant to be instantiated.
 */
public final class Constants {
    private Constants() {}

    // Table & columns
    public static final String EMAIL_TABLE = "email";
    public static final String EMAIL_COLUMN_RECIPIENT = "recipient_email";
    public static final String EMAIL_COLUMN_OTP = "otp";
    public static final String EMAIL_COLUMN_EXPIRATION_TIME = "expiration_time";
    public static final String EMAIL_COLUMN_IS_VERIFIED = "is_verified";

    // Indexes
    public static final String IDX_EMAIL_EXPIRATION_TIME = "idx_email_expiration_time";
    public static final String ID_EMAIL_RECIPIENT = "idx_email_recipient_email";

    // EMAIL_INVALID_FORMAT
    public static final String EMAIL_INVALID_FORMAT = "Invalid email format";

    // Table Logging
    public static final String EMAIL_LOGGING_TABLE = "email_logging";
    public static final String EMAIL_LOGGING_COLUMN_ACTION = "action";
    public static final String EMAIL_LOGGING_COLUMN_STATUS = "status";
    public static final String EMAIL_LOGGING_COLUMN_MESSAGE = "message";
    public static final String EMAIL_LOGGING_COLUMN_TIMESTAMP = "timestamp";
    public static final String EMAIL_LOGGING_COLUMN_RECIPIENT = "recipient_email";
    public static final String EMAIL_LOGGING_KAFKA_TOPIC = "email_kafka_topic";
    public static final String EMAIL_LOGGING_KAFKA_STATUS = "email_kafka_status";
}
