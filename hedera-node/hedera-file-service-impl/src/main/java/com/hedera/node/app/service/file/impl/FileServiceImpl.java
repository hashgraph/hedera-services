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
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandlerImpl;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/** Standard implementation of the {@link FileService} {@link Service}. */
public final class FileServiceImpl implements FileService {

    private static final int MAX_BLOBS = 4096;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();
    public static final String BLOBS_KEY = "BLOBS";

    private final FileAppendHandlerImpl fileAppendHandler;

    private final FileCreateHandlerImpl fileCreateHandler;

    private final FileDeleteHandlerImpl fileDeleteHandler;

    private final FileUpdateHandlerImpl fileUpdateHandler;

    private final FileGetContentsHandlerImpl fileGetContentsHandler;

    private final FileGetInfoHandlerImpl fileGetInfoHandler;

    private final FileSystemDeleteHandlerImpl fileSystemDeleteHandler;

    private final FileSystemUndeleteHandlerImpl fileSystemUndeleteHandler;

    /**
     * Creates a new {@link FileServiceImpl} instance.
     */
    public FileServiceImpl() {
        this.fileAppendHandler = new FileAppendHandlerImpl();
        this.fileCreateHandler = new FileCreateHandlerImpl();
        this.fileDeleteHandler = new FileDeleteHandlerImpl();
        this.fileUpdateHandler = new FileUpdateHandlerImpl();
        this.fileGetContentsHandler = new FileGetContentsHandlerImpl();
        this.fileGetInfoHandler = new FileGetInfoHandlerImpl();
        this.fileSystemDeleteHandler = new FileSystemDeleteHandlerImpl();
        this.fileSystemUndeleteHandler = new FileSystemUndeleteHandlerImpl();
    }

    /**
     * Returns the {@link FileAppendHandlerImpl} instance.
     *
     * @return the {@link FileAppendHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileAppendHandlerImpl getFileAppendHandler() {
        return fileAppendHandler;
    }

    /**
     * Returns the {@link FileCreateHandlerImpl} instance.
     *
     * @return the {@link FileCreateHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileCreateHandlerImpl getFileCreateHandler() {
        return fileCreateHandler;
    }

    /**
     * Returns the {@link FileDeleteHandlerImpl} instance.
     *
     * @return the {@link FileDeleteHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileDeleteHandlerImpl getFileDeleteHandler() {
        return fileDeleteHandler;
    }

    /**
     * Returns the {@link FileUpdateHandlerImpl} instance.
     *
     * @return the {@link FileUpdateHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileUpdateHandlerImpl getFileUpdateHandler() {
        return fileUpdateHandler;
    }

    /**
     * Returns the {@link FileGetContentsHandlerImpl} instance.
     *
     * @return the {@link FileGetContentsHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileGetContentsHandlerImpl getFileGetContentsHandler() {
        return fileGetContentsHandler;
    }

    /**
     * Returns the {@link FileGetInfoHandlerImpl} instance.
     *
     * @return the {@link FileGetInfoHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileGetInfoHandlerImpl getFileGetInfoHandler() {
        return fileGetInfoHandler;
    }

    /**
     * Returns the {@link FileSystemDeleteHandlerImpl} instance.
     *
     * @return the {@link FileSystemDeleteHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileSystemDeleteHandlerImpl getFileSystemDeleteHandler() {
        return fileSystemDeleteHandler;
    }

    /**
     * Returns the {@link FileSystemUndeleteHandlerImpl} instance.
     *
     * @return the {@link FileSystemUndeleteHandlerImpl} instance.
     */
    @NonNull
    @Override
    public FileSystemUndeleteHandlerImpl getFileSystemUndeleteHandler() {
        return fileSystemUndeleteHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandlers() {
        return Set.of(
                fileAppendHandler,
                fileCreateHandler,
                fileDeleteHandler,
                fileUpdateHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandlers() {
        return Set.of(fileGetContentsHandler, fileGetInfoHandler);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null").register(fileServiceSchema());
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
