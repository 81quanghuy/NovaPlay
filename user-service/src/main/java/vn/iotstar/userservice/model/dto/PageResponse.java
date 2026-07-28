package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Bao phân trang có hình dạng ổn định.
 * <p>
 * Serialize thẳng {@code Page}/{@code PageImpl} của Spring Data sẽ tạo ra một JSON không được
 * cam kết giữ nguyên giữa các phiên bản (Boot 3.3+ cảnh báo chính điều này), nên contract API
 * được định nghĩa tường minh ở đây.
 */
@Schema(name = "PageResponse", description = "Một trang kết quả.")
public record PageResponse<T>(

        @Schema(description = "Các phần tử của trang hiện tại.")
        List<T> content,

        @Schema(description = "Số thứ tự trang, bắt đầu từ 0.", example = "0")
        int page,

        @Schema(description = "Số phần tử tối đa mỗi trang.", example = "20")
        int size,

        @Schema(description = "Tổng số phần tử của tất cả các trang.", example = "137")
        long totalElements,

        @Schema(description = "Tổng số trang.", example = "7")
        int totalPages,

        @Schema(description = "Đây có phải trang cuối không.", example = "false")
        boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isLast()
        );
    }
}
