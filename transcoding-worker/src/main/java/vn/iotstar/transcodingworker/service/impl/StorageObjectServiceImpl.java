package vn.iotstar.transcodingworker.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import vn.iotstar.transcodingworker.service.StorageObjectService;
import vn.iotstar.transcodingworker.storage.StorageProvider;
import vn.iotstar.transcodingworker.storage.StorageProviderProperties;

import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StorageObjectServiceImpl implements StorageObjectService {

    private final Map<StorageProvider, S3Client> storageClients;
    private final StorageProviderProperties properties;

    @Override
    public void download(StorageProvider provider, String key, Path destination) {
        storageClients.get(provider).getObject(GetObjectRequest.builder()
                        .bucket(properties.get(provider).getBucketName())
                        .key(key)
                        .build(),
                destination);
    }

    @Override
    public void upload(StorageProvider provider, String key, Path source, String contentType) {
        storageClients.get(provider).putObject(PutObjectRequest.builder()
                        .bucket(properties.get(provider).getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(source));
    }
}
