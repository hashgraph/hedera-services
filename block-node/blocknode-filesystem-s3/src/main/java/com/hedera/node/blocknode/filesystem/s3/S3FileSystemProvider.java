package com.hedera.node.blocknode.filesystem.s3;

import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.node.blocknode.filesystem.api.FileSystemProvider;

public class S3FileSystemProvider implements FileSystemProvider {
    @Override
    public FileSystemApi getFileSystem() {
        // Provide implementation for S3FileSystem creation
        return new S3FileSystem();
    }
}
