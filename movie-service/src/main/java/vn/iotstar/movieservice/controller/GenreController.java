package vn.iotstar.movieservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.movieservice.model.dto.GenreRequest;
import vn.iotstar.movieservice.service.GenreService;
import vn.iotstar.movieservice.common.GenericResponse;

@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
@Tag(name = "Genres", description = "Thể loại phim: đọc công khai, quản trị cần ROLE_ADMIN.")
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    @Operation(summary = "Toàn bộ thể loại, sắp theo tên")
    public ResponseEntity<GenericResponse> findAll() {
        return ResponseEntity.ok(GenericResponse.success(genreService.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một thể loại")
    public ResponseEntity<GenericResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(genreService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo thể loại")
    public ResponseEntity<GenericResponse> create(@Valid @RequestBody GenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.success(
                genreService.create(request), "Đã tạo thể loại", HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Đổi tên thể loại; tên mới lan sang mọi phim đang dùng")
    public ResponseEntity<GenericResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody GenreRequest request) {
        return ResponseEntity.ok(
                GenericResponse.success(genreService.update(id, request), "Đã cập nhật thể loại"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoá thể loại; bị từ chối nếu còn phim đang dùng")
    public ResponseEntity<GenericResponse> delete(@PathVariable String id) {
        genreService.delete(id);
        return ResponseEntity.ok(GenericResponse.ok("Đã xoá thể loại"));
    }
}
