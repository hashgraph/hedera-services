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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.test.handlers.FileHandlerTestBase;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableFileStoreImplTest extends FileHandlerTestBase {
    private File file;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableFileStoreImpl(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new WritableFileStoreImpl(writableStates);
        assertNotNull(store);
    }

    @Test
    void commitsFileChanges() {
        file = createFile();
        assertFalse(writableFileState.contains(fileId));

        writableStore.put(file);

        assertTrue(writableFileState.contains(fileId));
        final var writtenTopic = writableFileState.get(fileId);
        assertEquals(file, writtenTopic);
    }

    @Test
    void getReturnsFile() {
        file = createFile();
        writableStore.put(file);

        final var maybeReadFile = writableStore.get(fileId.fileNum());

        assertTrue(maybeReadFile.isPresent());
        final var readFile = maybeReadFile.get();
        assertEquals(file, readFile);
    }

    @Test
    void verifyFileDeleted() {
        file = createFile();
        writableStore.put(file);

        final var maybeReadFile = writableStore.get(fileId.fileNum());

        assertTrue(maybeReadFile.isPresent());

        writableStore.removeFile(fileId.fileNum());

        final var readFile = writableStore.get(fileId.fileNum());
        assertEquals(readFile, Optional.empty());
    }
}
