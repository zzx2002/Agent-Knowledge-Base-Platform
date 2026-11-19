package com.agentkb.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 对象存储封装：原文件保存、下载、删除。
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.base-path}")
    private String basePath;

    public MinioStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /** 启动时确保 bucket 存在；中间件暂不可用时仅告警，不阻断应用启动。 */
    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 已创建: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败（后续可重试）: {}", e.getMessage());
        }
    }

    /** 生成对象键：{basePath}/{kbId}/{uuid}_{fileName} */
    public String buildObjectKey(Long kbId, String fileName) {
        String safeName = fileName == null ? "unnamed" : fileName.replace('\\', '_').replace('/', '_');
        String prefix = (basePath == null || basePath.isBlank()) ? "documents" : basePath;
        return prefix + "/" + kbId + "/" + UUID.randomUUID() + "_" + safeName;
    }

    public String upload(String objectName, InputStream inputStream, long size, String contentType) throws Exception {
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .stream(inputStream, size, -1)
                .build();
        ObjectWriteResponse response = minioClient.putObject(args);
        log.info("文件已上传 MinIO: bucket={}, object={}, etag={}", response.bucket(), response.object(), response.etag());
        return objectName;
    }

    public String upload(String objectName, MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            return upload(objectName, inputStream, file.getSize(), file.getContentType());
        }
    }

    public byte[] download(String objectName) throws Exception {
        GetObjectArgs args = GetObjectArgs.builder().bucket(bucket).object(objectName).build();
        try (GetObjectResponse response = minioClient.getObject(args)) {
            return response.readAllBytes();
        }
    }

    public void remove(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("MinIO 删除对象失败: object={}, error={}", objectName, e.getMessage());
        }
    }

    public String getBucket() {
        return bucket;
    }
}
