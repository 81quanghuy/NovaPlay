package vn.iotstar.notificationservice.model.enums;

/**
 * Kênh phát thông báo.
 * <p>
 * {@link #WEBSOCKET} và {@link #PUSH} được khai báo trước nhưng chưa có {@code NotificationChannel}
 * nào hiện thực. Chính sách định tuyến bỏ qua kênh không có bean tương ứng, nên khai báo sẵn ở đây
 * không gây tác dụng phụ.
 */
public enum ChannelType {
    EMAIL,
    IN_APP,
    WEBSOCKET,
    PUSH
}
