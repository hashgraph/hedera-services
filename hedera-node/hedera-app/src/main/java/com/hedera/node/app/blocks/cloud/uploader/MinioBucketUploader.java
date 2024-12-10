//package com.hedera.node.app.blocks.cloud.uploader;
//import com.hedera.node.app.annotations.CommonExecutor;
//
//import com.hedera.node.config.ConfigProvider;
//import com.hedera.node.config.data.BlockStreamConfig;
//import java.nio.file.Path;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import javax.inject.Inject;
//import javax.inject.Singleton;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import io.minio.MinioClient;
//
//@Singleton
//public class MinioBucketUploader implements CloudBucketUploader {
//    private static final Logger logger = LogManager.getLogger(MinioBucketUploader.class);
//    private final MinioClient minioClient;
//    private final String bucketName;
//    private final String provider;
//    private final ExecutorService uploadExecutor;
//    private final int maxRetryAttempts;
//
//    @Inject
//    public MinioBucketUploader(
//            BucketConfig config,
//            @CommonExecutor ExecutorService executor,
//            ConfigProvider configProvider) {
//        this.uploadExecutor = executor;
//        this.bucketName = config.name();
//        this.provider = config.provider();
//        this.maxRetryAttempts = configProvider.getConfiguration()
//                .getConfigData(BlockStreamConfig.class)
//                .uploadRetryAttempts();
//
//        this.minioClient = MinioClientFactory.createClient(config);
//    }
//    @Override
//    public CompletableFuture<Void> uploadBlock(Path blockPath) {
//        return CompletableFuture.runAsync(() -> {
//            String objectKey = getObjectKey(metadata.blockNumber());
//
//            try {
//                // First check if object already exists
//                if (blockExistsInternal(objectKey)) {
//                    String existingMd5 = getBlockMd5Internal(objectKey);
//                    if (existingMd5.equals(metadata.md5Hash())) {
//                        logger.debug("Block {} already exists with matching MD5", metadata.blockNumber());
//                        return;
//                    }
//                    throw new HashMismatchException(metadata.blockNumber(), provider);
//                }
//                // Upload with retry logic
//                RetryUtils.withRetry(() -> {
//                    minioClient.uploadObject(
//                            UploadObjectArgs.builder()
//                                    .bucket(bucketName)
//                                    .object(objectKey)
//                                    .filename(blockPath.toString())
//                                    .contentType("application/octet-stream")
//                                    .build());
//                    return null;
//                }, maxRetryAttempts);
//            } catch (Exception e) {
//                throw new CompletionException("Failed to upload block " + metadata.blockNumber(), e);
//            }
//        }, uploadExecutor);
//    }
//    @Override
//    public CompletableFuture<Boolean> blockExists(long blockNumber) {
//        return CompletableFuture.supplyAsync(
//                () -> blockExistsInternal(getObjectKey(blockNumber)),
//                uploadExecutor);
//    }
//    @Override
//    public CompletableFuture<String> getBlockMd5(long blockNumber) {
//        return CompletableFuture.supplyAsync(
//                () -> getBlockMd5Internal(getObjectKey(blockNumber)),
//                uploadExecutor);
//    }
//    @Override
//    public String getProvider() {
//        return provider;
//    }
//    private boolean blockExistsInternal(String objectKey) {
//        try {
//            minioClient.statObject(
//                    StatObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectKey)
//                            .build());
//            return true;
//        } catch (ErrorResponseException e) {
//            if (e.errorResponse().code().equals("NoSuchKey")) {
//                return false;
//            }
//            throw new CompletionException(e);
//        } catch (Exception e) {
//            throw new CompletionException(e);
//        }
//    }
//    private String getBlockMd5Internal(String objectKey) {
//        try {
//            var stat = minioClient.statObject(
//                    StatObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectKey)
//                            .build());
//            return stat.etag();
//        } catch (Exception e) {
//            throw new CompletionException(e);
//        }
//    }
//    private String getObjectKey(long blockNumber) {
//        return String.format("blocks/%d.blk", blockNumber);
//    }
//}