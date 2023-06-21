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
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.WritableUpdateFileStore;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableUpdateFileStoreTest {
    private WritableUpdateFileStore subject;

    @Mock(strictness = LENIENT)
    protected WritableStates writableStates;

    @Test
    void constructorCreatesFreezeState() {
        final WritableUpdateFileStore store = new WritableUpdateFileStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new WritableUpdateFileStore(null));
    }

    @Test
    void testPreparedUpdateFileID() {
        final AtomicReference<Long> backingStore = new AtomicReference<>(null);
        when(writableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_ID_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FreezeServiceImpl.UPGRADE_FILE_ID_KEY, backingStore::get, backingStore::set));

        final WritableUpdateFileStore store = new WritableUpdateFileStore(writableStates);

        // test with no file ID set
        assertTrue(store.updateFileID().isEmpty());

        // test with file ID set
        store.updateFileID(FileID.newBuilder().fileNum(42L).build());
        assertEquals(42L, store.updateFileID().get().fileNum());
    }

    @Test
    void testPreparedUpdateFileHash() {
        final AtomicReference<Bytes> backingStore = new AtomicReference<>(null);
        when(writableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get, backingStore::set));
        final WritableUpdateFileStore store = new WritableUpdateFileStore(writableStates);

        // test with no file hash set
        assertTrue(store.updateFileHash().isEmpty());

        // test with file hash set
        store.updateFileHash(Bytes.wrap("test hash"));
        assertEquals(Bytes.wrap("test hash"), store.updateFileHash().get());
    }
}
