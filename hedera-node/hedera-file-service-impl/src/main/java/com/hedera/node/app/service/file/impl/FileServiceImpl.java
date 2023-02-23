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
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FileService} {@link Service}. */
public final class FileServiceImpl implements FileService {

    private final FileAppendHandler fileAppendHandler;

    private final FileCreateHandler fileCreateHandler;

    private final FileDeleteHandler fileDeleteHandler;

    private final FileUpdateHandler fileUpdateHandler;

    private final FileGetContentsHandler fileGetContentsHandler;

    private final FileGetInfoHandler fileGetInfoHandler;

    private final FileSystemDeleteHandler fileSystemDeleteHandler;

    private final FileSystemUndeleteHandler fileSystemUndeleteHandler;

    public FileServiceImpl() {
        this.fileAppendHandler = new FileAppendHandler();
        this.fileCreateHandler = new FileCreateHandler();
        this.fileDeleteHandler = new FileDeleteHandler();
        this.fileUpdateHandler = new FileUpdateHandler();
        this.fileGetContentsHandler = new FileGetContentsHandler();
        this.fileGetInfoHandler = new FileGetInfoHandler();
        this.fileSystemDeleteHandler = new FileSystemDeleteHandler();
        this.fileSystemUndeleteHandler = new FileSystemUndeleteHandler();
    }

    @NonNull
    public FileAppendHandler getFileAppendHandler() {
        return fileAppendHandler;
    }

    @NonNull
    public FileCreateHandler getFileCreateHandler() {
        return fileCreateHandler;
    }

    @NonNull
    public FileDeleteHandler getFileDeleteHandler() {
        return fileDeleteHandler;
    }

    @NonNull
    public FileUpdateHandler getFileUpdateHandler() {
        return fileUpdateHandler;
    }

    @NonNull
    public FileGetContentsHandler getFileGetContentsHandler() {
        return fileGetContentsHandler;
    }

    @NonNull
    public FileGetInfoHandler getFileGetInfoHandler() {
        return fileGetInfoHandler;
    }

    @NonNull
    public FileSystemDeleteHandler getFileSystemDeleteHandler() {
        return fileSystemDeleteHandler;
    }

    @NonNull
    public FileSystemUndeleteHandler getFileSystemUndeleteHandler() {
        return fileSystemUndeleteHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
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
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(
                fileGetContentsHandler,
                fileGetInfoHandler);
    }
}
