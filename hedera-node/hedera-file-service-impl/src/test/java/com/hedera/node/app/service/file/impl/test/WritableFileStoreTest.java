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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableFileStoreTest extends FileTestBase {

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private StoreMetricsService storeMetricsService;

    private File file;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableFileStore(null, CONFIGURATION, storeMetricsService));
        assertThrows(
                NullPointerException.class, () -> new WritableFileStore(writableStates, null, storeMetricsService));
        assertThrows(NullPointerException.class, () -> new WritableFileStore(writableStates, CONFIGURATION, null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void constructorCreatesFileState() {
        final var store = new WritableFileStore(writableStates, CONFIGURATION, storeMetricsService);
        assertNotNull(store);
    }

    @Test
    void commitsFileChanges() {
        file = createFile();
        assertFalse(writableFileState.contains(fileId));

        writableStore.put(file);

        assertTrue(writableFileState.contains(fileId));
        final var writtenFile = writableFileState.get(fileId);
        assertEquals(file, writtenFile);
    }

    @Test
    void getReturnsFile() {
        file = createFile();
        writableStore.put(file);

        final var maybeReadFile = writableStore.get(fileId);

        assertTrue(maybeReadFile.isPresent());
        final var readFile = maybeReadFile.get();
        assertEquals(file, readFile);
    }

    @Test
    void verifyFileDeleted() {
        file = createFile();
        writableStore.put(file);

        final var maybeReadFile = writableStore.get(fileId);

        assertTrue(maybeReadFile.isPresent());

        writableStore.removeFile(fileId);

        final var readFile = writableStore.get(fileId);
        assertEquals(readFile, Optional.empty());
    }
}
