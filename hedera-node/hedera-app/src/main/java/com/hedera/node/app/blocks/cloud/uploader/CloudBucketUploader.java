package com.hedera.node.app.blocks.cloud.uploader;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface CloudBucketUploader {
    CompletableFuture<Void> uploadBlock(Path blockPath);
    CompletableFuture<Boolean> blockExists(long blockNumber);
    CompletableFuture<String> getBlockMd5(long blockNumber);
    String getProvider();
}
