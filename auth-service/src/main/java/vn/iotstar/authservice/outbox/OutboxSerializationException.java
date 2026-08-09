package vn.iotstar.authservice.outbox;

/**
 * Payload không serialize được thành JSON. Cố ý là unchecked và cố ý ném ra ngoài thay vì nuốt:
 * transaction nghiệp vụ phải rollback, vì một sự kiện không ghi được thì hành động sinh ra nó
 * cũng không được phép coi là đã xong.
 */
public class OutboxSerializationException extends RuntimeException {

    public OutboxSerializationException(String topic, Throwable cause) {
        super("Không serialize được payload cho topic " + topic, cause);
    }
}
