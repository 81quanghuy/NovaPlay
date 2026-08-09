package vn.iotstar.userservice.service.impl;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.model.dto.AddFavoriteItemRequest;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.repository.IFavoriteItemRepository;
import vn.iotstar.userservice.service.AuditLogger;
import vn.iotstar.userservice.service.UserProfileLookup;
import vn.iotstar.userservice.util.ContentType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteMoviesServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String USER_ID = "user-1";
    private static final String MOVIE_ID = "MOV_1";

    @Mock private IFavoriteItemRepository favoriteItemRepository;
    @Mock private UserProfileLookup userProfileLookup;
    @Mock private UserMetrics metrics;
    @Mock private AuditLogger auditLogger;
    @Mock private Counter counter;

    @InjectMocks private FavoriteMoviesServiceImpl service;

    private static final AddFavoriteItemRequest REQUEST =
            new AddFavoriteItemRequest(MOVIE_ID, ContentType.MOVIE);

    @BeforeEach
    void setUp() {
        when(userProfileLookup.resolveUserId(EMAIL)).thenReturn(USER_ID);
        when(metrics.getFavoriteAdded()).thenReturn(counter);
        when(metrics.getFavoriteRemoved()).thenReturn(counter);
    }

    @Test
    @DisplayName("thêm mục mới vào danh sách yêu thích")
    void addsNewFavorite() {
        when(favoriteItemRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(false);
        when(favoriteItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FavoriteItem result = service.addFavoriteMovie(EMAIL, REQUEST);

        ArgumentCaptor<FavoriteItem> captor = ArgumentCaptor.forClass(FavoriteItem.class);
        verify(favoriteItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getContentType()).isEqualTo(ContentType.MOVIE);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("thêm trùng bị từ chối bằng 409")
    void rejectsDuplicateFavorite() {
        when(favoriteItemRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.addFavoriteMovie(EMAIL, REQUEST))
                .isInstanceOf(vn.iotstar.userservice.exception.UserAlreadyExistsException.class);

        verify(favoriteItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("race giữa hai request song song cũng trả 409")
    void mapsDuplicateKeyRaceToConflict() {
        when(favoriteItemRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(false);
        when(favoriteItemRepository.save(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.addFavoriteMovie(EMAIL, REQUEST))
                .isInstanceOf(vn.iotstar.userservice.exception.UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("xoá mục không tồn tại là idempotent")
    void removeIsIdempotent() {
        service.removeFavoriteMovie(EMAIL, "khong-ton-tai");

        verify(favoriteItemRepository).deleteByUserIdAndMovieId(USER_ID, "khong-ton-tai");
    }

    @Test
    @DisplayName("trả về danh sách có phân trang")
    void returnsPagedFavorites() {
        Page<FavoriteItem> page = new PageImpl<>(List.of(
                FavoriteItem.builder().userId(USER_ID).movieId(MOVIE_ID)
                        .contentType(ContentType.MOVIE).build()));
        when(favoriteItemRepository.findByUserId(eq(USER_ID), any())).thenReturn(page);

        Page<FavoriteItem> result = service.getFavoriteMovies(EMAIL, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("xoá toàn bộ ghi nhận số lượng đã xoá")
    void clearsAllAndRecordsCount() {
        when(favoriteItemRepository.deleteAllByUserId(USER_ID)).thenReturn(7L);

        service.deleteAllFavoriteMovies(EMAIL);

        verify(auditLogger).favoritesCleared(EMAIL, 7L);
    }
}
