package vn.iotstar.movieservice.repository;

import vn.iotstar.movieservice.model.enums.MovieStatus;

/**
 * Bộ lọc cho truy vấn duyệt catalog. Field null nghĩa là "không lọc theo tiêu chí này".
 *
 * @param status  chỉ lấy phim ở trạng thái này; endpoint công khai luôn truyền PUBLISHED
 * @param genreId lọc theo mã thể loại, khớp vào mảng genres nhúng trong phim
 * @param series  true chỉ lấy phim bộ, false chỉ lấy phim lẻ, null lấy cả hai
 * @param q       từ khoá tìm trong tiêu đề, dùng text index
 */
public record MovieSearchCriteria(
        MovieStatus status,
        String genreId,
        Boolean series,
        String q
) {}
