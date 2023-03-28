/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.file.impl;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/** Standard implementation of the {@link FileService} {@link com.hedera.node.app.spi.Service}. */
public final class FileServiceImpl implements FileService {
    private static final int MAX_BLOBS = 4096;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();
    public static final String BLOBS_KEY = "BLOBS";

    private final FileAppendHandler fileAppendHandler;

    private final FileCreateHandler fileCreateHandler;

    private final FileDeleteHandler fileDeleteHandler;

    private final FileGetContentsHandler fileGetContentsHandler;

    private final FileGetInfoHandler fileGetInfoHandler;

    private final FileSystemDeleteHandler fileSystemDeleteHandler;

    private final FileSystemUndeleteHandler fileSystemUndeleteHandler;

    private final FileUpdateHandler fileUpdateHandler;

    /**
     * WARNING: This contructor is only used to let the code compile. All places that call this constructor must be
     * refactored to use Dagger instead.
     *
     * @deprecated DO NOT USE THIS CONSTRUCTOR.
     */
    @Deprecated(forRemoval = true)
    public FileServiceImpl() {
        this(
                new FileAppendHandler(),
                new FileCreateHandler(),
                new FileDeleteHandler(),
                new FileGetContentsHandler(),
                new FileGetInfoHandler(),
                new FileSystemDeleteHandler(),
                new FileSystemUndeleteHandler(),
                new FileUpdateHandler());
    }

    public FileServiceImpl(@NonNull final FileAppendHandler fileAppendHandler,
            @NonNull final FileCreateHandler fileCreateHandler,
            @NonNull final FileDeleteHandler fileDeleteHandler,
            @NonNull final FileGetContentsHandler fileGetContentsHandler,
            @NonNull final FileGetInfoHandler fileGetInfoHandler,
            @NonNull final FileSystemDeleteHandler fileSystemDeleteHandler,
            @NonNull final FileSystemUndeleteHandler fileSystemUndeleteHandler,
            @NonNull final FileUpdateHandler fileUpdateHandler) {
        this.fileAppendHandler = Objects.requireNonNull(fileAppendHandler);
        this.fileCreateHandler = Objects.requireNonNull(fileCreateHandler);
        this.fileDeleteHandler = Objects.requireNonNull(fileDeleteHandler);
        this.fileGetContentsHandler = Objects.requireNonNull(fileGetContentsHandler);
        this.fileGetInfoHandler = Objects.requireNonNull(fileGetInfoHandler);
        this.fileSystemDeleteHandler = Objects.requireNonNull(fileSystemDeleteHandler);
        this.fileSystemUndeleteHandler = Objects.requireNonNull(fileSystemUndeleteHandler);
        this.fileUpdateHandler = Objects.requireNonNull(fileUpdateHandler);
    }

    public FileAppendHandler getFileAppendHandler() {
        return fileAppendHandler;
    }

    public FileCreateHandler getFileCreateHandler() {
        return fileCreateHandler;
    }

    public FileDeleteHandler getFileDeleteHandler() {
        return fileDeleteHandler;
    }

    public FileGetContentsHandler getFileGetContentsHandler() {
        return fileGetContentsHandler;
    }

    public FileGetInfoHandler getFileGetInfoHandler() {
        return fileGetInfoHandler;
    }

    public FileSystemDeleteHandler getFileSystemDeleteHandler() {
        return fileSystemDeleteHandler;
    }

    public FileSystemUndeleteHandler getFileSystemUndeleteHandler() {
        return fileSystemUndeleteHandler;
    }

    public FileUpdateHandler getFileUpdateHandler() {
        return fileUpdateHandler;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(fileServiceSchema());
    }

    private Schema fileServiceSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(blobsDef());
            }
        };
    }

    private static StateDefinition<VirtualBlobKey, VirtualBlobValue> blobsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION, VirtualBlobKey::new, new VirtualBlobKeySerializer());
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForVirtualValue(VirtualBlobValue.CURRENT_VERSION, VirtualBlobValue::new);

        return StateDefinition.onDisk(BLOBS_KEY, keySerdes, valueSerdes, MAX_BLOBS);
    }
}
