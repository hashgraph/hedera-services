/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.services;

import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

/**
 * This module is the Dagger entry point to the {@link com.hedera.node.app.service.file.FileService}.The module provides
 * all handlers of the service as managed objects in the {@link Singleton} scope. This interface should never be used
 * directly in code. It is only used as a base for Dagger to create the code that provides all handlers.
 */
@Module
public interface FileServiceModule {

    @Provides
    @Singleton
    @NonNull
    static FileAppendHandler fileAppendHandler() {
        return new FileAppendHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileCreateHandler fileCreateHandler() {
        return new FileCreateHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileDeleteHandler fileDeleteHandler() {
        return new FileDeleteHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileGetContentsHandler fileGetContentsHandler() {
        return new FileGetContentsHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileGetInfoHandler fileGetInfoHandler() {
        return new FileGetInfoHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileSystemDeleteHandler fileSystemDeleteHandler() {
        return new FileSystemDeleteHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileSystemUndeleteHandler fileSystemUndeleteHandler() {
        return new FileSystemUndeleteHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileUpdateHandler fileUpdateHandler() {
        return new FileUpdateHandler();
    }

    @Provides
    @Singleton
    @NonNull
    static FileServiceImpl fileService(
            @NonNull final FileAppendHandler fileAppendHandler,
            @NonNull final FileCreateHandler fileCreateHandler,
            @NonNull final FileDeleteHandler fileDeleteHandler,
            @NonNull final FileGetContentsHandler fileGetContentsHandler,
            @NonNull final FileGetInfoHandler fileGetInfoHandler,
            @NonNull final FileSystemDeleteHandler fileSystemDeleteHandler,
            @NonNull final FileSystemUndeleteHandler fileSystemUndeleteHandler,
            @NonNull final FileUpdateHandler fileUpdateHandler) {
        return new FileServiceImpl(
                fileAppendHandler,
                fileCreateHandler,
                fileDeleteHandler,
                fileGetContentsHandler,
                fileGetInfoHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                fileUpdateHandler);
    }
}
