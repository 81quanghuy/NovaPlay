package vn.iotstar.mediaservice.service;

import vn.iotstar.mediaservice.storage.StorageProvider;

/**
 * Thao tác object storage, tách khỏi AWS SDK để {@link MediaService}/{@code MediaServiceImpl} không
 * còn phụ thuộc trực tiếp vào {@code S3Client}/{@code S3Presigner}. Mọi method nhận tường minh
 * {@link StorageProvider} làm tham số đầu — không có state ẩn nào quyết định provider nào được dùng,
 * caller (thường là {@code MediaServiceImpl}) chịu trách nhiệm chọn đúng provider (cờ sống cho upload
 * mới, hoặc provider đã lưu trên {@code Media} cho record cũ — xem
 * {@link vn.iotstar.mediaservice.storage.StorageProviderResolver}).
 */
public interface MediaStorageService {

    /** Presigned PUT URL, ký cả Content-Type/Content-Length để S3 ép đúng lúc upload thật. */
    String generatePresignedUploadUrl(StorageProvider provider, String key, String contentType, long contentLength);

    boolean doesObjectExist(StorageProvider provider, String key);

    /** Không nuốt lỗi — dùng khi thao tác xoá phải chặn luôn bước tiếp theo nếu thất bại. */
    void deleteObject(StorageProvider provider, String key);

    /** Best-effort, nuốt lỗi + log — dùng khi dọn object mồ côi không được chặn luồng chính. */
    void deleteObjectQuietly(StorageProvider provider, String key);

    String generateCdnUrl(StorageProvider provider, String key);
}
