package vn.iotstar.movieservice.model.enums;

/**
 * Vòng đời hiển thị của một phim trong catalog.
 * <p>
 * Endpoint công khai chỉ trả về phim {@link #PUBLISHED}. Nhờ vậy admin dựng được phim còn thiếu
 * poster, mô tả hay danh sách tập mà khách chưa nhìn thấy.
 */
public enum MovieStatus {
    DRAFT,
    PUBLISHED
}
