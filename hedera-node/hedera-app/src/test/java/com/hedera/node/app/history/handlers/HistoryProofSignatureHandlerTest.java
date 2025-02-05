/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.hapi.node.state.history.History;
import com.hedera.hapi.node.state.history.HistorySignature;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.history.HistoryProofSignatureTransactionBody;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.history.impl.ProofController;
import com.hedera.node.app.history.impl.ProofControllers;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryProofSignatureHandlerTest {
    private static final long NODE_ID = 123L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final HistorySignature HISTORY_SIGNATURE =
            new HistorySignature(new History(Bytes.wrap("AB"), Bytes.wrap("metadata")), Bytes.wrap("signature"));

    @Mock
    private ProofControllers controllers;

    @Mock
    private PreHandleContext preHandleContext;

    @Mock
    private HandleContext context;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private ProofController controller;

    @Mock
    private WritableHistoryStore store;

    @Mock
    private StoreFactory factory;

    @Mock
    private PureChecksContext pureChecksContext;

    private HistoryProofSignatureHandler subject;

    @BeforeEach
    void setUp() {
        subject = new HistoryProofSignatureHandler(controllers);
    }

    @Test
    void pureChecksAndPreHandleDoNothing() {
        assertDoesNotThrow(() -> subject.pureChecks(pureChecksContext));
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));
    }

    @Test
    void handleIsNoopWithoutActiveConstruction() {
        givenSignatureWith(1L, HistorySignature.DEFAULT);

        subject.handle(context);

        verify(controllers).getInProgressById(1L);
        verifyNoMoreInteractions(context);
    }

    @Test
    void doesNotSaveSignatureUnlessControllerChangesState() {
        givenSignatureWith(1L, HISTORY_SIGNATURE);
        given(controllers.getInProgressById(1L)).willReturn(Optional.of(controller));
        given(context.creatorInfo()).willReturn(nodeInfo);
        given(context.consensusNow()).willReturn(CONSENSUS_NOW);
        given(nodeInfo.nodeId()).willReturn(NODE_ID);

        subject.handle(context);

        final var captor = ArgumentCaptor.forClass(ReadableHistoryStore.HistorySignaturePublication.class);
        verify(controller).addSignaturePublication(captor.capture());
        final var publication = captor.getValue();
        assertEquals(NODE_ID, publication.nodeId());
        assertEquals(HISTORY_SIGNATURE, publication.signature());
        assertEquals(CONSENSUS_NOW, publication.at());
    }

    @Test
    void savesSignatureIfControllerChangesState() {
        givenSignatureWith(1L, HISTORY_SIGNATURE);
        given(controllers.getInProgressById(1L)).willReturn(Optional.of(controller));
        given(context.creatorInfo()).willReturn(nodeInfo);
        given(context.consensusNow()).willReturn(CONSENSUS_NOW);
        given(nodeInfo.nodeId()).willReturn(NODE_ID);
        final var captor = ArgumentCaptor.forClass(ReadableHistoryStore.HistorySignaturePublication.class);
        given(controller.addSignaturePublication(any())).willReturn(true);
        given(context.storeFactory()).willReturn(factory);
        given(factory.writableStore(WritableHistoryStore.class)).willReturn(store);

        subject.handle(context);

        verify(controller).addSignaturePublication(captor.capture());
        final var publication = captor.getValue();
        verify(store).addSignature(1L, publication);
    }

    private void givenSignatureWith(final long constructionId, @NonNull final HistorySignature signature) {
        final var op = new HistoryProofSignatureTransactionBody(constructionId, signature);
        final var body = TransactionBody.newBuilder().historyProofSignature(op).build();
        given(context.body()).willReturn(body);
    }
}
