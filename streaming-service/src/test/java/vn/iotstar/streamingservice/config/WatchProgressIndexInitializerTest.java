package vn.iotstar.streamingservice.config;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import vn.iotstar.streamingservice.entity.WatchProgress;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.iotstar.streamingservice.utils.Constants.IDX_WATCH_PROGRESS_UNIQUE;

/**
 * Thứ tự field của compound index là thứ dễ hỏng nhất ở đây: bản trước dùng {@code Map.of} nên
 * thứ tự duyệt bị xáo theo salt ngẫu nhiên của từng lần chạy JVM — index tạo ra sai thứ tự khoá,
 * và lần khởi động sau MongoDB trả lỗi 86 IndexKeySpecsConflict làm pod chết lúc boot.
 */
@ExtendWith(MockitoExtension.class)
class WatchProgressIndexInitializerTest {

    private static final List<String> EXPECTED_KEYS = List.of("userEmail", "movieId", "episodeNumber");

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    private void run() {
        when(mongoTemplate.indexOps(WatchProgress.class)).thenReturn(indexOperations);
        new WatchProgressIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
    }

    private static IndexInfo indexOf(String name, boolean unique, List<String> keys) {
        List<IndexField> fields = keys.stream()
                .map(k -> IndexField.create(k, Sort.Direction.ASC))
                .toList();
        return new IndexInfo(fields, name, unique, false, "");
    }

    private Document createdKeys() {
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        return captor.getValue().getIndexKeys();
    }

    @Test
    @DisplayName("collection trống: tạo index đúng thứ tự khoá userEmail -> movieId -> episodeNumber")
    void createsIndexWithDeterministicKeyOrder() {
        when(indexOperations.getIndexInfo()).thenReturn(List.of());

        run();

        // Thứ tự khoá là một phần định danh index trong MongoDB, không phải chi tiết thẩm mỹ.
        assertThat(createdKeys().keySet()).containsExactlyElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("index đã đúng: không tạo lại, không drop")
    void skipsWhenCorrectIndexAlreadyExists() {
        when(indexOperations.getIndexInfo())
                .thenReturn(List.of(indexOf(IDX_WATCH_PROGRESS_UNIQUE, true, EXPECTED_KEYS)));

        run();

        verify(indexOperations, never()).createIndex(any());
        verify(indexOperations, never()).dropIndex(any());
    }

    @Test
    @DisplayName("index cùng tên nhưng sai thứ tự khoá: drop rồi tạo lại thay vì chết bằng lỗi 86")
    void dropsAndRecreatesIndexWithSameNameButDifferentKeys() {
        when(indexOperations.getIndexInfo()).thenReturn(List.of(indexOf(
                IDX_WATCH_PROGRESS_UNIQUE, true, List.of("episodeNumber", "movieId", "userEmail"))));

        run();

        verify(indexOperations).dropIndex(IDX_WATCH_PROGRESS_UNIQUE);
        assertThat(createdKeys().keySet()).containsExactlyElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("index đúng khoá nhưng không unique: drop rồi tạo lại thành unique")
    void recreatesNonUniqueIndex() {
        when(indexOperations.getIndexInfo())
                .thenReturn(List.of(indexOf("auto_generated_idx", false, EXPECTED_KEYS)));

        run();

        verify(indexOperations).dropIndex("auto_generated_idx");
        assertThat(createdKeys().keySet()).containsExactlyElementsOf(EXPECTED_KEYS);
    }
}
