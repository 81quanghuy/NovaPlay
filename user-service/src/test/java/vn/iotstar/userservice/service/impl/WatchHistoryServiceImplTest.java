package vn.iotstar.userservice.service.impl;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.model.dto.UpsertWatchProgressRequest;
import vn.iotstar.userservice.model.entity.WatchProgress;
import vn.iotstar.userservice.repository.IWatchProgressRepository;
import vn.iotstar.userservice.service.UserProfileLookup;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchHistoryServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String USER_ID = "user-1";
    private static final String MOVIE_ID = "MOV_1";

    @Mock private IWatchProgressRepository watchProgressRepository;
    @Mock private UserProfileLookup userProfileLookup;
    @Mock private UserMetrics metrics;
    @Mock private Counter counter;

    @InjectMocks private WatchHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(userProfileLookup.resolveUserId(EMAIL)).thenReturn(USER_ID);
        when(metrics.getWatchProgressUpserted()).thenReturn(counter);
        when(watchProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(watchProgressRepository.findByUserIdAndMovieId(USER_ID, MOVIE_ID))
                .thenReturn(Optional.empty());
    }

    private static UpsertWatchProgressRequest request(long positionMs, Long durationMs, Boolean ended) {
        return new UpsertWatchProgressRequest(
                MOVIE_ID, null, null, null, positionMs, durationMs, ended);
    }

    @ParameterizedTest(name = "position={0} duration={1} -> percent={2}")
    @DisplayName("percent luôn nằm trong [0, 100]")
    @CsvSource({
            "0,      1000, 0",
            "500,    1000, 50",
            "1000,   1000, 100",
            // durationMs là snapshot client gửi lên và có thể lệch; phép chia thô sẽ ra 200.
            "2000,   1000, 100"
    })
    void clampsPercentIntoValidRange(long positionMs, long durationMs, int expectedPercent) {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(positionMs, durationMs, null));

        assertThat(result.getPercent()).isEqualTo(expectedPercent);
    }

    @Test
    @DisplayName("percent là null khi durationMs không xác định")
    void leavesPercentNullWhenDurationUnknown() {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(500, null, null));

        assertThat(result.getPercent()).isNull();
        assertThat(result.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("durationMs bằng 0 không gây chia cho 0")
    void handlesZeroDurationWithoutDividingByZero() {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(500, 0L, null));

        assertThat(result.getPercent()).isNull();
    }

    @Test
    @DisplayName("đánh dấu completed khi vượt ngưỡng 95%")
    void marksCompletedAtThreshold() {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(950, 1000L, null));

        assertThat(result.getPercent()).isEqualTo(95);
        assertThat(result.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("chưa completed khi dưới ngưỡng")
    void notCompletedBelowThreshold() {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(940, 1000L, null));

        assertThat(result.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("client báo ended thì completed kể cả khi percent thấp")
    void honoursClientEndedFlag() {
        WatchProgress result = service.upsertWatchProgress(EMAIL, request(10, 1000L, true));

        assertThat(result.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("cập nhật bản ghi cũ thay vì tạo mới")
    void updatesExistingRecordInPlace() {
        WatchProgress existing = WatchProgress.builder()
                .watchHistoryId("wp-1").userId(USER_ID).movieId(MOVIE_ID).positionMs(100)
                .build();
        when(watchProgressRepository.findByUserIdAndMovieId(USER_ID, MOVIE_ID))
                .thenReturn(Optional.of(existing));

        WatchProgress result = service.upsertWatchProgress(EMAIL, request(750, 1000L, null));

        assertThat(result.getWatchHistoryId()).isEqualTo("wp-1");
        assertThat(result.getPositionMs()).isEqualTo(750);
        assertThat(result.getLastWatchedAt()).isNotNull();
    }
}
