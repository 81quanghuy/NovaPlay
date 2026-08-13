package vn.iotstar.streamingservice.service;

import vn.iotstar.streamingservice.storage.StorageProvider;

import java.time.Duration;

public interface HlsStorageService {

    /** Đọc nguyên văn một object text nhỏ (playlist .m3u8) — server tự đọc để viết lại URI bên trong. */
    String readTextObject(StorageProvider provider, String key);

    /** Presigned GET URL cho một object (segment .ts, hoặc chính playlist nếu cần) — trình duyệt tải thẳng. */
    String presignGetObject(StorageProvider provider, String key, Duration ttl);
}
