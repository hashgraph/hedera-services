/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileHandlers {

    private final FileAppendHandler fileAppendHandler;

    private final FileCreateHandler fileCreateHandler;

    private final FileDeleteHandler fileDeleteHandler;

    private final FileGetContentsHandler fileGetContentsHandler;

    private final FileGetInfoHandler fileGetInfoHandler;

    private final FileSystemDeleteHandler fileSystemDeleteHandler;

    private final FileSystemUndeleteHandler fileSystemUndeleteHandler;

    private final FileUpdateHandler fileUpdateHandler;

    @Inject
    public FileHandlers(
            @NonNull final FileAppendHandler fileAppendHandler,
            @NonNull final FileCreateHandler fileCreateHandler,
            @NonNull final FileDeleteHandler fileDeleteHandler,
            @NonNull final FileGetContentsHandler fileGetContentsHandler,
            @NonNull final FileGetInfoHandler fileGetInfoHandler,
            @NonNull final FileSystemDeleteHandler fileSystemDeleteHandler,
            @NonNull final FileSystemUndeleteHandler fileSystemUndeleteHandler,
            @NonNull final FileUpdateHandler fileUpdateHandler) {
        this.fileAppendHandler = requireNonNull(fileAppendHandler, "fileAppendHandler must not be null");
        this.fileCreateHandler = requireNonNull(fileCreateHandler, "fileCreateHandler must not be null");
        this.fileDeleteHandler = requireNonNull(fileDeleteHandler, "fileDeleteHandler must not be null");
        this.fileGetContentsHandler = requireNonNull(fileGetContentsHandler, "fileGetContentsHandler must not be null");
        this.fileGetInfoHandler = requireNonNull(fileGetInfoHandler, "fileGetInfoHandler must not be null");
        this.fileSystemDeleteHandler =
                requireNonNull(fileSystemDeleteHandler, "fileSystemDeleteHandler must not be null");
        this.fileSystemUndeleteHandler =
                requireNonNull(fileSystemUndeleteHandler, "fileSystemUndeleteHandler must not be null");
        this.fileUpdateHandler = requireNonNull(fileUpdateHandler, "fileUpdateHandler must not be null");
    }

    public FileAppendHandler fileAppendHandler() {
        return fileAppendHandler;
    }

    public FileCreateHandler fileCreateHandler() {
        return fileCreateHandler;
    }

    public FileDeleteHandler fileDeleteHandler() {
        return fileDeleteHandler;
    }

    public FileGetContentsHandler fileGetContentsHandler() {
        return fileGetContentsHandler;
    }

    public FileGetInfoHandler fileGetInfoHandler() {
        return fileGetInfoHandler;
    }

    public FileSystemDeleteHandler fileSystemDeleteHandler() {
        return fileSystemDeleteHandler;
    }

    public FileSystemUndeleteHandler fileSystemUndeleteHandler() {
        return fileSystemUndeleteHandler;
    }

    public FileUpdateHandler fileUpdateHandler() {
        return fileUpdateHandler;
    }
}
