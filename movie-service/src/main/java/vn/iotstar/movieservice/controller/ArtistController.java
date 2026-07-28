package vn.iotstar.movieservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.movieservice.model.dto.ArtistRequest;
import vn.iotstar.movieservice.service.ArtistService;
import vn.iotstar.utils.constants.GenericResponse;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
@Tag(name = "Artists", description = "Nghệ sĩ: đọc công khai, quản trị cần ROLE_ADMIN.")
public class ArtistController {

    private final ArtistService artistService;

    @GetMapping
    @Operation(summary = "Tìm nghệ sĩ theo tên, có phân trang")
    public ResponseEntity<GenericResponse> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {

        // Nghệ sĩ chỉ sắp theo tên; ngày phát hành không có nghĩa với tài nguyên này.
        Pageable pageable = PageableFactory.of(page, size, "fullName", "asc",
                PageableFactory.ARTIST_SORT_FIELDS, "fullName");
        return ResponseEntity.ok(GenericResponse.success(artistService.search(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một nghệ sĩ")
    public ResponseEntity<GenericResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(artistService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo nghệ sĩ")
    public ResponseEntity<GenericResponse> create(@Valid @RequestBody ArtistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.success(
                artistService.create(request), "Đã tạo nghệ sĩ", HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật nghệ sĩ; đổi tên sẽ lan sang ê-kíp của mọi phim liên quan")
    public ResponseEntity<GenericResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody ArtistRequest request) {
        return ResponseEntity.ok(
                GenericResponse.success(artistService.update(id, request), "Đã cập nhật nghệ sĩ"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoá nghệ sĩ; bị từ chối nếu còn xuất hiện trong phim")
    public ResponseEntity<GenericResponse> delete(@PathVariable String id) {
        artistService.delete(id);
        return ResponseEntity.ok(GenericResponse.ok("Đã xoá nghệ sĩ"));
    }
}
