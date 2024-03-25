package com.hedera.node.blocknode.core;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeFileSystemConfig;
import com.hedera.node.blocknode.config.types.FileSystem;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.node.blocknode.filesystem.local.LocalFileSystemProvider;
import com.hedera.node.blocknode.filesystem.s3.S3FileSystemProvider;

public class FileSystemApiProvider {
    public FileSystemApi createFileSystem(ConfigProvider configProvider) {
        final var fileSystemConfig = configProvider.getConfiguration().getConfigData(BlockNodeFileSystemConfig.class);
        FileSystemApi fileSystemApi = fileSystemConfig.fileSystem() == FileSystem.LOCAL
                ? new LocalFileSystemProvider().getFileSystem()
                : new S3FileSystemProvider().getFileSystem();
        return fileSystemApi;
    }
}
