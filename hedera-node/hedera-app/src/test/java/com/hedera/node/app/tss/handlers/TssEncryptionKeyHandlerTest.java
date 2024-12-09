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

package com.hedera.node.app.tss.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NodeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssEncryptionKeyHandlerTest {

    @Mock
    private PreHandleContext preHandleContext;

    @Mock
    private HandleContext handleContext;

    @Mock
    private WritableTssStore tssBaseStore;

    @Mock
    private TssEncryptionKeyTransactionBody tssEncryptionKeyTransactionBody;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private NodeInfo nodeInfo;

    private TssEncryptionKeyHandler tssEncryptionKeyHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tssEncryptionKeyHandler = new TssEncryptionKeyHandler();
    }

    @Test
    void preHandleDoesNotThrowWhenContextIsValid() {
        assertDoesNotThrow(() -> tssEncryptionKeyHandler.preHandle(preHandleContext));
    }

    @Test
    void pureChecksDoesNotThrowWhenTransactionBodyIsValid() {
        assertDoesNotThrow(() -> tssEncryptionKeyHandler.pureChecks(transactionBody));
    }

    @Test
    void handleStoresNewEncryptionKeyWhenNoExistingKey() throws HandleException {
        when(handleContext.body()).thenReturn(transactionBody);
        when(transactionBody.tssEncryptionKeyOrThrow()).thenReturn(tssEncryptionKeyTransactionBody);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);
        when(tssEncryptionKeyTransactionBody.publicTssEncryptionKey()).thenReturn(Bytes.wrap("newKey"));
        when(tssBaseStore.getTssEncryptionKeys(1L)).thenReturn(null);
        when(handleContext.creatorInfo()).thenReturn(nodeInfo);
        when(nodeInfo.nodeId()).thenReturn(1L);

        tssEncryptionKeyHandler.handle(handleContext);

        verify(tssBaseStore).put(any(EntityNumber.class), any(TssEncryptionKeys.class));
    }

    @Test
    void handleUpdatesEncryptionKeyWhenExistingKey() throws HandleException {
        when(handleContext.body()).thenReturn(transactionBody);
        when(transactionBody.tssEncryptionKeyOrThrow()).thenReturn(tssEncryptionKeyTransactionBody);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);
        when(tssEncryptionKeyTransactionBody.publicTssEncryptionKey()).thenReturn(Bytes.wrap("newKey"));
        TssEncryptionKeys existingKeys = TssEncryptionKeys.newBuilder()
                .currentEncryptionKey(Bytes.wrap("oldKey"))
                .build();
        when(tssBaseStore.getTssEncryptionKeys(1L)).thenReturn(existingKeys);
        when(handleContext.creatorInfo()).thenReturn(nodeInfo);
        when(nodeInfo.nodeId()).thenReturn(1L);

        tssEncryptionKeyHandler.handle(handleContext);

        verify(tssBaseStore).put(any(EntityNumber.class), any(TssEncryptionKeys.class));
    }
}
