package vn.iotstar.streamingservice.service;

public interface ViewCountService {

    /**
     * Ghi nhận một lượt xem, dedup theo {@code (userEmail, movieId)} trong một cửa sổ ngắn — gọi
     * lại nhiều lần trong lúc đang xem (player thường tự gọi lặp) không được tính thêm view.
     */
    void recordView(String userEmail, String movieId);
}
