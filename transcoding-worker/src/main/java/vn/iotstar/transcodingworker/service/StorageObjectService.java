package vn.iotstar.transcodingworker.service;

import vn.iotstar.transcodingworker.storage.StorageProvider;

import java.nio.file.Path;

public interface StorageObjectService {

    /** Tải object về một file cục bộ (scratch). */
    void download(StorageProvider provider, String key, Path destination);

    /** Upload một file cục bộ lên object storage tại {@code key}. */
    void upload(StorageProvider provider, String key, Path source, String contentType);
}
