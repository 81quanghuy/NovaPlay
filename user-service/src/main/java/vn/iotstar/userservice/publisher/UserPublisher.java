package vn.iotstar.userservice.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.iotstar.userservice.repository.IUserProfileRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserPublisher {
    private final IUserProfileRepository repo;
    private final StreamBridge bridge;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
//        List<UserProfile> batch = repo.findPending();
//        for (UserProfile e : batch) {
//            try {
//                Message<String> msg = MessageBuilder.withPayload(e.getUsername())
//                        .setHeader("aggregateId", e.getAddress())
//                        .build();
//                boolean ok = bridge.send(e.getUserId(), msg);
//                if (ok) {
//                    e.setIsOnline(true);
//                    repo.save(e);
//                } else {
//                    log.warn("Failed to send to {} (will retry)", e.getGender());
//                }
//            } catch (Exception ex) {
//                log.error("Publish error", ex);
//            }
//        }
    }
}
