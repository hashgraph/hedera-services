/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.handlers.FileHandlers;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileHandlersTest {
    private FileAppendHandler fileAppendHandler;
    private FileCreateHandler fileCreateHandler;
    private FileDeleteHandler fileDeleteHandler;
    private FileGetContentsHandler fileGetContentsHandler;
    private FileGetInfoHandler fileGetInfoHandler;
    private FileSystemDeleteHandler fileSystemDeleteHandler;
    private FileSystemUndeleteHandler fileSystemUndeleteHandler;
    private FileUpdateHandler fileUpdateHandler;

    private FileHandlers fileHandlers;

    @BeforeEach
    public void setUp() {
        fileAppendHandler = mock(FileAppendHandler.class);
        fileCreateHandler = mock(FileCreateHandler.class);
        fileDeleteHandler = mock(FileDeleteHandler.class);
        fileGetContentsHandler = mock(FileGetContentsHandler.class);
        fileGetInfoHandler = mock(FileGetInfoHandler.class);
        fileSystemDeleteHandler = mock(FileSystemDeleteHandler.class);
        fileSystemUndeleteHandler = mock(FileSystemUndeleteHandler.class);
        fileUpdateHandler = mock(FileUpdateHandler.class);

        fileHandlers = new FileHandlers(
                fileAppendHandler,
                fileCreateHandler,
                fileDeleteHandler,
                fileGetContentsHandler,
                fileGetInfoHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                fileUpdateHandler);
    }

    @Test
    void fileAppendHandlerReturnsCorrectInstance() {
        assertEquals(
                fileAppendHandler,
                fileHandlers.fileAppendHandler(),
                "fileAppendHandler does not return correct instance");
    }

    @Test
    void fileCreateHandlerReturnsCorrectInstance() {
        assertEquals(
                fileCreateHandler,
                fileHandlers.fileCreateHandler(),
                "fileCreateHandler does not return correct instance");
    }

    @Test
    void fileDeleteHandlerReturnsCorrectInstance() {
        assertEquals(
                fileDeleteHandler,
                fileHandlers.fileDeleteHandler(),
                "fileDeleteHandler does not return correct instance");
    }

    @Test
    void fileGetContentsHandlerReturnsCorrectInstance() {
        assertEquals(
                fileGetContentsHandler,
                fileHandlers.fileGetContentsHandler(),
                "fileGetContentsHandler does not return correct instance");
    }

    @Test
    void fileGetInfoHandlerReturnsCorrectInstance() {
        assertEquals(
                fileGetInfoHandler,
                fileHandlers.fileGetInfoHandler(),
                "fileGetInfoHandler does not return correct instance");
    }

    @Test
    void fileSystemDeleteHandlerReturnsCorrectInstance() {
        assertEquals(
                fileSystemDeleteHandler,
                fileHandlers.fileSystemDeleteHandler(),
                "fileSystemDeleteHandler does not return correct instance");
    }

    @Test
    void fileSystemUndeleteHandlerReturnsCorrectInstance() {
        assertEquals(
                fileSystemUndeleteHandler,
                fileHandlers.fileSystemUndeleteHandler(),
                "fileSystemUndeleteHandler does not return correct instance");
    }

    @Test
    void fileUpdateHandlerReturnsCorrectInstance() {
        assertEquals(
                fileUpdateHandler,
                fileHandlers.fileUpdateHandler(),
                "fileUpdateHandler does not return correct instance");
    }
}
