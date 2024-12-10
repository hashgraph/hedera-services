package com.hedera.node.app.blocks.cloud.uploader;


public class HashMismatchException extends RuntimeException {
    public HashMismatchException(long blockNumber, String provider) {
        super(String.format("Hash mismatch for block %d in provider %s", blockNumber, provider));
    }
}