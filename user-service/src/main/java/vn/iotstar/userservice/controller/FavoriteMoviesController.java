package vn.iotstar.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.userservice.mapper.FavoriteItemMapper;
import vn.iotstar.userservice.model.dto.AddFavoriteItemRequest;
import vn.iotstar.userservice.model.dto.FavoriteItemDTO;
import vn.iotstar.userservice.model.dto.PageResponse;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.service.FavoriteMoviesService;
import vn.iotstar.utils.constants.GenericResponse;

import java.util.Map;

import static vn.iotstar.userservice.controller.PaginationLimits.MAX_PAGE_SIZE;

@RestController
@RequestMapping("/api/v1/users/favorites")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "Favorites API", description = "Quản lý danh sách yêu thích (My List)")
public class FavoriteMoviesController {

    private final FavoriteMoviesService favoriteMoviesService;

    @Operation(summary = "Thêm nội dung vào danh sách yêu thích", security = @SecurityRequirement(name = "bearer-jwt"))
    @PostMapping
    public ResponseEntity<GenericResponse> addFavoriteMovie(
            @RequestHeader("X-User-Email") String email,
            @Valid @RequestBody AddFavoriteItemRequest request) {
        FavoriteItem item = favoriteMoviesService.addFavoriteMovie(email, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(FavoriteItemMapper.toDto(item), "Added to favorites"));
    }

    @Operation(summary = "Xóa nội dung khỏi danh sách yêu thích", security = @SecurityRequirement(name = "bearer-jwt"))
    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> removeFavoriteMovie(
            @RequestHeader("X-User-Email") String email,
            @PathVariable String movieId) {
        favoriteMoviesService.removeFavoriteMovie(email, movieId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách yêu thích (có phân trang)", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping
    public ResponseEntity<GenericResponse> getFavoriteMovies(
            @RequestHeader("X-User-Email") String email,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        PageResponse<FavoriteItemDTO> result = PageResponse.from(
                favoriteMoviesService.getFavoriteMovies(email, PageRequest.of(page, size)),
                FavoriteItemMapper::toDto);
        return ResponseEntity.ok(GenericResponse.success(result, "Favorites retrieved"));
    }

    @Operation(summary = "Kiểm tra nội dung có trong danh sách yêu thích không", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping("/{movieId}/exists")
    public ResponseEntity<GenericResponse> isFavoriteMovie(
            @RequestHeader("X-User-Email") String email,
            @PathVariable String movieId) {
        boolean exists = favoriteMoviesService.isFavoriteMovie(email, movieId);
        return ResponseEntity.ok(GenericResponse.success(Map.of("exists", exists), "Favorite check result"));
    }

    @Operation(summary = "Xóa toàn bộ danh sách yêu thích", security = @SecurityRequirement(name = "bearer-jwt"))
    @DeleteMapping
    public ResponseEntity<Void> deleteAllFavoriteMovies(@RequestHeader("X-User-Email") String email) {
        favoriteMoviesService.deleteAllFavoriteMovies(email);
        return ResponseEntity.noContent().build();
    }
}
