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

package com.hedera.node.app.tss.stores;

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_ENCRYPTION_KEY_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_STATUS_KEY;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTssStoreTest {
    @Mock
    private WritableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessageState;

    @Mock
    private WritableKVState<TssVoteMapKey, TssVoteTransactionBody> tssVoteState;

    @Mock
    private WritableKVState<EntityNumber, TssEncryptionKeyTransactionBody> tssEncryptionKeyState;

    @Mock
    private WritableSingletonState<TssStatus> tssStatusState;

    @Mock
    private WritableStates states;

    private WritableTssStore tssStore;

    @BeforeEach
    void setUp() {
        when(states.<TssMessageMapKey, TssMessageTransactionBody>get(TSS_MESSAGE_MAP_KEY))
                .thenReturn(tssMessageState);
        when(states.<TssVoteMapKey, TssVoteTransactionBody>get(TSS_VOTE_MAP_KEY))
                .thenReturn(tssVoteState);
        when(states.<EntityNumber, TssEncryptionKeyTransactionBody>get(TSS_ENCRYPTION_KEY_MAP_KEY))
                .thenReturn(tssEncryptionKeyState);
        when(states.<TssStatus>getSingleton(TSS_STATUS_KEY)).thenReturn(tssStatusState);

        tssStore = new WritableTssStore(states);
    }

    @Test
    void testPutTssMessage() {
        TssMessageMapKey key = TssMessageMapKey.DEFAULT;
        TssMessageTransactionBody body = TssMessageTransactionBody.DEFAULT;
        tssStore.put(key, body);
        verify(tssMessageState).put(key, body);
    }

    @Test
    void testPutTssVote() {
        TssVoteMapKey key = TssVoteMapKey.DEFAULT;
        TssVoteTransactionBody body = TssVoteTransactionBody.DEFAULT;
        tssStore.put(key, body);
        verify(tssVoteState).put(key, body);
    }

    @Test
    void testPutEncryptionKey() {
        EntityNumber entityNumber = new EntityNumber(1);
        TssEncryptionKeyTransactionBody body = TssEncryptionKeyTransactionBody.DEFAULT;
        tssStore.put(entityNumber, body);
        verify(tssEncryptionKeyState).put(entityNumber, body);
    }

    @Test
    void testPutTssStatus() {
        TssStatus status = TssStatus.DEFAULT;
        tssStore.put(status);
        verify(tssStatusState).put(status);
    }

    @Test
    void testRemoveTssMessage() {
        TssMessageMapKey key = TssMessageMapKey.DEFAULT;
        tssStore.remove(key);
        verify(tssMessageState).remove(key);
    }

    @Test
    void testRemoveTssVote() {
        TssVoteMapKey key = TssVoteMapKey.DEFAULT;
        tssStore.remove(key);
        verify(tssVoteState).remove(key);
    }

    @Test
    void testRemoveEncryptionKey() {
        EntityNumber entityNumber = new EntityNumber(1);
        tssStore.remove(entityNumber);
        verify(tssEncryptionKeyState).remove(entityNumber);
    }

    @Test
    void testRemoveEncryptionKeyIfPresent() {
        EntityNumber entityNumber = new EntityNumber(1);
        tssEncryptionKeyState.put(entityNumber, TssEncryptionKeyTransactionBody.DEFAULT);
        given(tssEncryptionKeyState.keys()).willReturn(List.of(entityNumber).iterator());

        final var rosterEntries = new HashSet<>(List.of(new EntityNumber(1), new EntityNumber(3)));
        tssStore.removeIfNotPresent(rosterEntries);

        verify(tssEncryptionKeyState, times(0)).remove(entityNumber);
    }

    @Test
    void testRemoveEncryptionKeyIfNotPresent() {
        EntityNumber entityNumber = new EntityNumber(1);
        tssEncryptionKeyState.put(entityNumber, TssEncryptionKeyTransactionBody.DEFAULT);
        given(tssEncryptionKeyState.keys()).willReturn(List.of(entityNumber).iterator());

        final var rosterEntries = new HashSet<>(List.of(new EntityNumber(2), new EntityNumber(3)));
        tssStore.removeIfNotPresent(rosterEntries);
        verify(tssEncryptionKeyState).remove(entityNumber);
    }

    @Test
    void testClear() {
        when(tssVoteState.keys()).thenReturn(mock(Iterator.class));
        when(tssMessageState.keys()).thenReturn(mock(Iterator.class));
        when(tssEncryptionKeyState.keys()).thenReturn(mock(Iterator.class));

        tssStore.clear();

        verify(tssVoteState).keys();
        verify(tssMessageState).keys();
        verify(tssEncryptionKeyState).keys();
        verify(tssStatusState).put(TssStatus.DEFAULT);
    }
}
