package com.hedera.node.blocknode.filesystem.local;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.node.blocknode.filesystem.api.FileSystemProvider;

public class LocalFileSystemProvider implements FileSystemProvider {

    @Override
    public FileSystemApi getFileSystem() {
        return new LocalFileSystem(new ConfigProvider());
    }
}
