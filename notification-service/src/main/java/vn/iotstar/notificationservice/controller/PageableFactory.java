package vn.iotstar.notificationservice.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.iotstar.notificationservice.exception.BadRequestException;

import java.util.Set;

/**
 * Dựng {@link Pageable} có giới hạn từ tham số truy vấn thô.
 * <p>
 * Không dùng {@code Pageable} resolver mặc định của Spring Data: nó cho phép client tự đặt
 * {@code size} tuỳ ý và {@code sort} theo field bất kỳ. {@code size} lớn kéo cả collection vào bộ
 * nhớ, còn sort theo field không có index buộc MongoDB sắp xếp toàn bộ trong RAM.
 */
final class PageableFactory {

    private PageableFactory() {}

    static final int MAX_PAGE_SIZE = 100;

    /** Cả hai field đều có index (xem {@code MongoIndexInitializer}). */
    static final Set<String> NOTIFICATION_SORT_FIELDS = Set.of("createdAt");

    static Pageable of(int page, int size, String sort, String direction,
                       Set<String> allowedFields, String defaultField) {
        if (page < 0) {
            throw new BadRequestException("page không được âm");
        }
        if (size < 1) {
            throw new BadRequestException("size phải lớn hơn 0");
        }

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);

        String sortField = (sort == null || sort.isBlank()) ? defaultField : sort.trim();
        if (!allowedFields.contains(sortField)) {
            throw new BadRequestException(
                    "Chỉ được sắp xếp theo: " + String.join(", ", allowedFields));
        }

        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(page, cappedSize, Sort.by(dir, sortField));
    }
}
