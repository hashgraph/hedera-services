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
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.ReadableSpecialFileStoreImpl;
import com.hedera.node.app.service.networkadmin.impl.WritableSpecialFileStore;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecialFileStoreTest {
    private WritableSpecialFileStore subject;

    @Mock(strictness = LENIENT)
    protected WritableStates writableStates;

    @Test
    void constructorCreatesFreezeState() {
        final WritableSpecialFileStore store = new WritableSpecialFileStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new WritableSpecialFileStore(null));
        assertThrows(NullPointerException.class, () -> new ReadableSpecialFileStoreImpl(null));
    }

    @Test
    void testPreparedUpdateFileNum() {
        AtomicReference<Long> backingStore = new AtomicReference<>(null);
        when(writableStates.getSingleton(FreezeServiceImpl.PREPARED_UPGRADE_FILEID_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FreezeServiceImpl.PREPARED_UPGRADE_FILEID_KEY, backingStore::get, backingStore::set));

        final WritableSpecialFileStore store = new WritableSpecialFileStore(writableStates);
        store.preparedUpdateFileID(FileID.newBuilder().fileNum(42L).build());

        assertEquals(42L, store.preparedUpdateFileID().fileNum());
    }

    @Test
    void testPreparedUpdateFileHash() {
        AtomicReference<Bytes> backingStore = new AtomicReference<>(null);
        when(writableStates.getSingleton(FreezeServiceImpl.PREPARED_UPGRADE_FILE_HASH_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FreezeServiceImpl.PREPARED_UPGRADE_FILE_HASH_KEY, backingStore::get, backingStore::set));
        final WritableSpecialFileStore store = new WritableSpecialFileStore(writableStates);
        store.preparedUpdateFileHash(Bytes.wrap("test hash"));

        assertEquals(Bytes.wrap("test hash"), store.preparedUpdateFileHash());
    }
}
