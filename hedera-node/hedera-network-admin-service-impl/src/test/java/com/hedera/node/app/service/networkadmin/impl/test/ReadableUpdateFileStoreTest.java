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

package com.hedera.node.app.service.networkadmin.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.service.networkadmin.ReadableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.ReadableUpdateFileStoreImpl;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableUpdateFileStoreTest {
    private ReadableUpdateFileStore subject;

    @Mock(strictness = LENIENT)
    protected ReadableStates readableStates;

    @Test
    void constructorCreatesFreezeState() {
        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new ReadableUpdateFileStoreImpl(null));
    }

    @Test
    void testGetFileByFileID() {
        final FileID fileId = FileID.newBuilder().fileNum(42L).build();
        final byte[] fileBytes = "bogus file bytes".getBytes();
        final MapReadableKVState<FileID, byte[]> state = MapReadableKVState.<FileID, byte[]>builder(
                        FreezeServiceImpl.UPGRADE_FILES_KEY)
                .value(fileId, fileBytes)
                .build();
        when(readableStates.get(FreezeServiceImpl.UPGRADE_FILES_KEY)).then(invocation -> state);

        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);
        assertEquals(fileBytes, store.get(fileId).get());
    }

    @Test
    void testEmptyGetFileByFileID() {
        final FileID fileId = FileID.newBuilder().fileNum(42L).build();
        final MapReadableKVState<FileID, byte[]> state = MapReadableKVState.<FileID, byte[]>builder(
                        FreezeServiceImpl.UPGRADE_FILES_KEY)
                .build();
        when(readableStates.get(FreezeServiceImpl.UPGRADE_FILES_KEY)).then(invocation -> state);

        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);
        assertTrue(store.get(fileId).isEmpty());
    }

    @Test
    void testPreparedUpdateFileID() {
        final FileID fileId = FileID.newBuilder().fileNum(42L).build();
        final AtomicReference<FileID> backingStore = new AtomicReference<>(fileId);
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_ID_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_ID_KEY, backingStore::get));

        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);

        assertEquals(42L, store.updateFileID().get().fileNum());
    }

    @Test
    void testEmptyPreparedUpdateFileID() {
        final AtomicReference<FileID> backingStore = new AtomicReference<>(); // contains null
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_ID_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_ID_KEY, backingStore::get));

        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);

        assertTrue(store.updateFileID().isEmpty());
    }

    @Test
    void testPreparedUpdateFileHash() {
        final Bytes fileBytes = Bytes.wrap("test hash");
        final AtomicReference<Bytes> backingStore = new AtomicReference<>(fileBytes);
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get));
        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);
        assertEquals(Bytes.wrap("test hash"), store.updateFileHash().get());
    }

    @Test
    void testEmptyPreparedUpdateFileHash() {
        final AtomicReference<Bytes> backingStore = new AtomicReference<>(); // contains null
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get));
        final ReadableUpdateFileStore store = new ReadableUpdateFileStoreImpl(readableStates);
        assertTrue(store.updateFileHash().isEmpty());
    }
}
