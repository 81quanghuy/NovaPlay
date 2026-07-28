package vn.iotstar.userservice.util;

/**
 * Loại nội dung mà người dùng có thể thêm vào danh sách yêu thích.
 * Lưu xuống Mongo ở field {@code movie_type} dưới dạng chuỗi tên enum.
 */
public enum ContentType {
    MOVIE,
    EPISODE,
    SEASON,
    SERIES
}
