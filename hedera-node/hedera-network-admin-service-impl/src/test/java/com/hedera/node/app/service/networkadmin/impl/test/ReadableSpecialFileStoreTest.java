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
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.service.networkadmin.ReadableSpecialFileStore;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.ReadableSpecialFileStoreImpl;
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
class ReadableSpecialFileStoreTest {
    private ReadableSpecialFileStore subject;

    @Mock(strictness = LENIENT)
    protected ReadableStates readableStates;

    @Test
    void constructorCreatesFreezeState() {
        final ReadableSpecialFileStore store = new ReadableSpecialFileStoreImpl(readableStates);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new ReadableSpecialFileStoreImpl(null));
    }

    @Test
    void testGetFileByFileID() {
        FileID fileId = FileID.newBuilder().fileNum(42L).build();
        byte[] fileBytes = "bogus file bytes".getBytes();
        MapReadableKVState<FileID, byte[]> state = MapReadableKVState.<FileID, byte[]>builder(
                        FreezeServiceImpl.UPGRADE_FILES_KEY)
                .value(fileId, fileBytes)
                .build();
        when(readableStates.get(FreezeServiceImpl.UPGRADE_FILES_KEY)).then(invocation -> state);

        final ReadableSpecialFileStore store = new ReadableSpecialFileStoreImpl(readableStates);

        assertEquals(fileBytes, store.get(fileId).get());
    }

    @Test
    void testPreparedUpdateFileID() {
        FileID fileId = FileID.newBuilder().fileNum(42L).build();
        AtomicReference<FileID> backingStore = new AtomicReference<>(fileId);
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILEID_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILEID_KEY, backingStore::get));

        final ReadableSpecialFileStore store = new ReadableSpecialFileStoreImpl(readableStates);

        assertEquals(42L, store.updateFileID().get().fileNum());
    }

    @Test
    void testPreparedUpdateFileHash() {
        Bytes fileBytes = Bytes.wrap("test hash");
        AtomicReference<Bytes> backingStore = new AtomicReference<>(fileBytes);
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get));
        final ReadableSpecialFileStore store = new ReadableSpecialFileStoreImpl(readableStates);
        assertEquals(Bytes.wrap("test hash"), store.updateFileHash().get());
    }
}
