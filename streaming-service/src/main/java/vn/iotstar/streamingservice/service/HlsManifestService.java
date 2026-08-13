package vn.iotstar.streamingservice.service;

import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;

import java.time.Duration;

public interface HlsManifestService {

    /**
     * Master playlist được SINH TRỰC TIẾP từ metadata rendition trong manifest (bandwidth/
     * resolution), không đọc lại file ffmpeg đã tạo — tránh phải parse ngược một định dạng ffmpeg
     * tự sinh, và dữ liệu trong manifest đã đủ để dựng lại chính xác.
     */
    String buildMasterPlaylist(VideoManifestResponse manifest, String playbackToken);

    /**
     * Playlist rendition PHẢI đọc từ file ffmpeg thật (danh sách segment + thời lượng không thể
     * suy ra từ metadata) rồi viết lại: URI khoá trỏ về endpoint phát khoá của chính service này,
     * mỗi segment thành presigned GET URL trỏ thẳng vào storage.
     */
    String buildRenditionPlaylist(VideoManifestResponse manifest, String label, String playbackToken, Duration segmentUrlTtl);
}
