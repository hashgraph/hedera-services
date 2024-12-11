package com.hedera.node.app.blocks.cloud.uploader;


public class HashMismatchException extends RuntimeException {
    public HashMismatchException(String objectKey, String provider) {
        super(String.format("Hash mismatch for block %d in provider %s", objectKey, provider));
    }
}