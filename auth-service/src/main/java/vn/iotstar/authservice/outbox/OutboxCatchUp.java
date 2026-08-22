package vn.iotstar.authservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Nhặt những row mà không pod nào còn nhớ tới — hậu quả của việc một pod chết sau khi COMMIT
 * nhưng trước khi Kafka ack, hoặc của notification bị mất trong lúc connection LISTEN đứt.
 * <p>
 * Cố ý KHÔNG mang {@code @Scheduled} trên chính nó — việc lên lịch thuộc về {@link OutboxPoller},
 * để bật/tắt và chỉnh chu kỳ được qua cấu hình mà không phải sửa lớp này. Các thời điểm gọi:
 * lúc khởi động, sau mỗi lần kết nối lại thành công (xem {@link OutboxNotificationListener}),
 * theo chu kỳ của {@link OutboxPoller}, và khi được gọi tay qua {@link OutboxCatchUpController}.
 * <p>
 * An toàn khi nhiều pod chạy đồng thời: {@code claimBatch} nhận việc bằng
 * {@code FOR UPDATE SKIP LOCKED} nên hai pod chia nhau row thay vì gửi trùng.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxCatchUp {

    private final OutboxDao dao;
    private final OutboxRelayService relayService;
    private final OutboxProperties properties;

    /** @return tổng số row đã nhận việc và đưa đi gửi */
    public int run() {
        int tong = 0;
        while (true) {
            List<OutboxRecord> lo = dao.claimBatch(properties.getBatchSize());
            if (lo.isEmpty()) {
                break;
            }
            // Gọi thẳng send: câu claimBatch ở trên CHÍNH LÀ bước nhận việc. Đi qua relay(id)
            // sẽ nhận việc lần nữa và tăng attempts hai lần cho cùng một lượt gửi.
            for (OutboxRecord record : lo) {
                try {
                    relayService.send(record);
                } catch (Exception e) {
                    // Một row hỏng không được phép dừng toàn bộ catch-up. Ghi log lỗi và tiếp tục
                    // row vẫn được tính là đã nhận việc (claimBatch đã claim nó), và run() sẽ trả
                    // về count đã nhận, dù gửi thành công hay không. Nếu gửi thực sự thất bại,
                    // OutboxRelayService đã cập nhật next_attempt_at, sẽ thử lại ở catch-up kế tiếp.
                    log.error("Lỗi gửi record mồ côi: id={}, topic={}", record.id(), record.topic(), e);
                }
            }
            tong += lo.size();

            if (lo.size() < properties.getBatchSize()) {
                break;
            }
        }

        if (tong > 0) {
            log.info("Catch-up outbox đã nhặt {} row mồ côi", tong);
        }
        return tong;
    }
}
