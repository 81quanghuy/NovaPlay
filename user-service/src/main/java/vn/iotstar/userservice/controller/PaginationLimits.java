package vn.iotstar.userservice.controller;

/**
 * Giới hạn phân trang dùng chung cho các endpoint danh sách.
 */
final class PaginationLimits {

    /**
     * Chặn trên cho tham số {@code size}. Không có giới hạn này, một client có thể yêu cầu
     * hàng triệu bản ghi trong một request và kéo sập service.
     */
    static final int MAX_PAGE_SIZE = 100;

    private PaginationLimits() {}
}
