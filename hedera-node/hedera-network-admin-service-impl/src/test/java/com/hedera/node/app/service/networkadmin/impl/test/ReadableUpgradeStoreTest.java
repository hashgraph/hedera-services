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

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.networkadmin.ReadableUpgradeStore;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.ReadableUpgradeStoreImpl;
import com.hedera.node.app.spi.state.ReadableSingletonStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableUpgradeStoreTest {
    private ReadableUpgradeStore subject;

    @Mock(strictness = LENIENT)
    protected ReadableStates readableStates;

    @Test
    void constructorCreatesFreezeState() {
        final ReadableUpgradeStore store = new ReadableUpgradeStoreImpl(readableStates);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new ReadableUpgradeStoreImpl(null));
    }

    @Test
    void testUpdateFileHash() {
        final ProtoBytes hashBytes = new ProtoBytes(Bytes.wrap("test hash"));
        final AtomicReference<ProtoBytes> backingStore = new AtomicReference<>(hashBytes);
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get));
        final ReadableUpgradeStore store = new ReadableUpgradeStoreImpl(readableStates);
        assertEquals(Bytes.wrap("test hash"), store.updateFileHash().get());
    }

    @Test
    void testEmptyUpdateFileHash() {
        final AtomicReference<Bytes> backingStore = new AtomicReference<>(); // contains null
        when(readableStates.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY))
                .then(invocation ->
                        new ReadableSingletonStateBase<>(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, backingStore::get));
        final ReadableUpgradeStore store = new ReadableUpgradeStoreImpl(readableStates);
        assertTrue(store.updateFileHash().isEmpty());
    }
}
