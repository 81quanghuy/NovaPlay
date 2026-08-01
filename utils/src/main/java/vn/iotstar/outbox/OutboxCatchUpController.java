package vn.iotstar.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Chạy tay một vòng catch-up, dùng khi cảnh báo {@code outbox_pending > 0} kéo dài nổ.
 * <p>
 * Không lộ ra ngoài: api-gateway chỉ route {@code /api/v1/**} và {@code /swagger/**}, nên
 * {@code /internal/**} chỉ gọi được từ trong cluster.
 */
@RestController
@RequestMapping("/internal/outbox")
@RequiredArgsConstructor
public class OutboxCatchUpController {

    private final OutboxCatchUp catchUp;

    @PostMapping("/catch-up")
    public ResponseEntity<Map<String, Integer>> chayCatchUp() {
        return ResponseEntity.ok(Map.of("claimed", catchUp.run()));
    }
}
