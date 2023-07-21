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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.ReadableUpgradeStoreImpl;
import com.hedera.node.app.spi.fixtures.state.ListReadableQueueState;
import com.hedera.node.app.spi.state.ReadableSingletonStateBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableUpgradeStoreImplTest extends FileTestBase {
    private ReadableUpgradeStoreImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableUpgradeStoreImpl(filteredReadableStates);
    }

    @Test
    void getsFileMetadataIfUpgradeFileExists() {
        givenValidUpgradeFile(false, true);
        final var file = subject.peek();

        assertNotNull(file);

        assertEquals(fileSystemFileId, file.fileId());
        assertEquals(keys, file.keys());

        assertEquals(memo, file.memo());
        assertFalse(file.deleted());
        assertEquals(Bytes.wrap(contents), file.contents());
    }

    @Test
    void missingUpgradeFileIsNull() {
        final var stateData =
                ListReadableQueueState.<Bytes>builder(UPGRADE_DATA_KEY).build();
        final AtomicReference<File> backingStore = new AtomicReference<>();
        final var stateFile = new ReadableSingletonStateBase<>(UPGRADE_FILE_KEY, backingStore::get);
        ;
        given(filteredReadableStates.<Bytes>getQueue(UPGRADE_DATA_KEY)).willReturn(stateData);
        given(filteredReadableStates.<File>getSingleton(UPGRADE_FILE_KEY)).willReturn(stateFile);
        subject = new ReadableUpgradeStoreImpl(filteredReadableStates);
        assertThat(subject.peek()).isNull();
    }

    @Test
    void constructorCreatesUpgradeFileState() {
        final var store = new ReadableUpgradeStoreImpl(filteredReadableStates);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableUpgradeStoreImpl(null));
    }

    @Test
    void validGetFullFileContent() throws IOException {
        givenValidFile();
        assertTrue(subject.getFull().length() > 0);
    }
}
