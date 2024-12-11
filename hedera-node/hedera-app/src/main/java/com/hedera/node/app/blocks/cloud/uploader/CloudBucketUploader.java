package com.hedera.node.app.blocks.cloud.uploader;

import com.hedera.node.config.types.BucketProvider;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface CloudBucketUploader {
    CompletableFuture<Void> uploadBlock(Path blockPath);
    CompletableFuture<Boolean> blockExists(String objectKey);
    CompletableFuture<String> getBlockMd5(String objectKey);
    BucketProvider getProvider();
}
