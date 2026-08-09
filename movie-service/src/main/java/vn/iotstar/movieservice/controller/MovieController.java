package vn.iotstar.movieservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.movieservice.model.dto.*;
import vn.iotstar.movieservice.model.enums.MovieStatus;
import vn.iotstar.movieservice.service.MovieService;
import vn.iotstar.movieservice.common.GenericResponse;

/**
 * Phân quyền theo method chứ không theo đường dẫn: GET là công khai, mọi thao tác ghi đều yêu cầu
 * ROLE_ADMIN. Nhờ vậy không cần một cây đường dẫn {@code /admin} song song, và gateway chỉ phải
 * biết đúng một prefix cho toàn bộ tài nguyên này.
 */
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
@Tag(name = "Movies", description = "Catalog phim: duyệt công khai, quản trị cần ROLE_ADMIN.")
public class MovieController {

    private final MovieService movieService;

    // ---------- Công khai ----------

    @GetMapping
    @Operation(summary = "Duyệt catalog phim đã phát hành")
    public ResponseEntity<GenericResponse> browse(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String genreId,
            @RequestParam(required = false) Boolean series,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageableFactory.of(page, size, sort, direction,
                PageableFactory.MOVIE_SORT_FIELDS, "releaseDate");
        PageResponse<MovieSummaryDTO> result =
                movieService.browsePublished(genreId, series, q, pageable);

        return ResponseEntity.ok(GenericResponse.success(result));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Chi tiết một phim đã phát hành, tra theo id hoặc slug")
    public ResponseEntity<GenericResponse> detail(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(
                GenericResponse.success(movieService.findPublishedByIdOrSlug(idOrSlug)));
    }

    // ---------- Quản trị ----------

    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách phim mọi trạng thái, kể cả DRAFT")
    public ResponseEntity<GenericResponse> manage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(required = false) String genreId,
            @RequestParam(required = false) Boolean series,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageableFactory.of(page, size, sort, direction,
                PageableFactory.MOVIE_SORT_FIELDS, "releaseDate");
        PageResponse<MovieSummaryDTO> result =
                movieService.searchAll(status, genreId, series, q, pageable);

        return ResponseEntity.ok(GenericResponse.success(result));
    }

    @GetMapping("/manage/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chi tiết một phim bất kể trạng thái")
    public ResponseEntity<GenericResponse> detailForAdmin(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(movieService.findByIdForAdmin(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo phim mới; luôn ở trạng thái DRAFT")
    public ResponseEntity<GenericResponse> create(@Valid @RequestBody MovieRequest request) {
        MovieDetailDTO created = movieService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(created, "Đã tạo phim", HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật phim")
    public ResponseEntity<GenericResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody MovieRequest request) {
        return ResponseEntity.ok(
                GenericResponse.success(movieService.update(id, request), "Đã cập nhật phim"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Phát hành hoặc gỡ phát hành phim")
    public ResponseEntity<GenericResponse> changeStatus(
            @PathVariable String id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(GenericResponse.success(
                movieService.changeStatus(id, request.status()), "Đã đổi trạng thái phim"));
    }

    @PutMapping("/{id}/episodes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thay toàn bộ danh sách tập của phim bộ")
    public ResponseEntity<GenericResponse> replaceEpisodes(
            @PathVariable String id,
            @Valid @RequestBody ReplaceEpisodesRequest request) {
        return ResponseEntity.ok(GenericResponse.success(
                movieService.replaceEpisodes(id, request.episodes()), "Đã cập nhật danh sách tập"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoá phim")
    public ResponseEntity<GenericResponse> delete(@PathVariable String id) {
        movieService.delete(id);
        return ResponseEntity.ok(GenericResponse.ok("Đã xoá phim"));
    }

    /** Body của endpoint đổi trạng thái; tách record riêng để có validation và tài liệu rõ ràng. */
    public record ChangeStatusRequest(@NotNull MovieStatus status) {}
}
