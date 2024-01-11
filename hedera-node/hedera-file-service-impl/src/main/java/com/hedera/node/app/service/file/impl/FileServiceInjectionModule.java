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

package com.hedera.node.app.service.file.impl;

import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.handlers.FileHandlers;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import dagger.Module;

@Module
public interface FileServiceInjectionModule {

    FileAppendHandler fileAppendHandler();

    FileCreateHandler fileCreateHandler();

    FileDeleteHandler fileDeleteHandler();

    FileGetContentsHandler fileGetContentsHandler();

    FileGetInfoHandler fileGetInfoHandler();

    FileSystemDeleteHandler fileSystemDeleteHandler();

    FileSystemUndeleteHandler fileSystemUndeleteHandler();

    FileUpdateHandler fileUpdateHandler();

    FileHandlers fileHandlers();
}
