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

package com.hedera.node.app.hints.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.NodePartyId;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsContextTest {
    private static final Bytes BLOCK_HASH = Bytes.wrap("BH");
    private static final Bytes VERIFICATION_KEY = Bytes.wrap("VK");
    private static final Bytes AGGREGATION_KEY = Bytes.wrap("AK");
    private static final PreprocessedKeys PREPROCESSED_KEYS = new PreprocessedKeys(AGGREGATION_KEY, VERIFICATION_KEY);
    private static final NodePartyId A_NODE_PARTY_ID = new NodePartyId(1L, 2);
    private static final NodePartyId B_NODE_PARTY_ID = new NodePartyId(3L, 6);
    private static final NodePartyId C_NODE_PARTY_ID = new NodePartyId(7L, 14);
    private static final NodePartyId D_NODE_PARTY_ID = new NodePartyId(9L, 18);
    private static final HintsConstruction CONSTRUCTION = HintsConstruction.newBuilder()
            .constructionId(1L)
            .preprocessedKeys(PREPROCESSED_KEYS)
            .nodePartyIds(List.of(A_NODE_PARTY_ID, B_NODE_PARTY_ID, C_NODE_PARTY_ID, D_NODE_PARTY_ID))
            .build();

    @Mock
    private HintsLibrary library;

    @Mock
    private BlsSignature signature;

    @Mock
    private BlsPublicKey badKey;

    @Mock
    private BlsPublicKey goodKey;

    private HintsContext subject;

    @BeforeEach
    void setUp() {
        subject = new HintsContext(library);
    }

    @Test
    void becomesReadyOnceConstructionSet() {
        assertFalse(subject.isReady());
        assertThrows(IllegalStateException.class, subject::constructionIdOrThrow);
        assertThrows(IllegalStateException.class, subject::verificationKeyOrThrow);

        subject.setConstruction(CONSTRUCTION);

        assertTrue(subject.isReady());

        assertEquals(CONSTRUCTION.constructionId(), subject.constructionIdOrThrow());
        assertEquals(VERIFICATION_KEY, subject.verificationKeyOrThrow());
    }

    @Test
    void signingWorksAsExpectedFor() {
        given(library.extractPublicKey(AGGREGATION_KEY, A_NODE_PARTY_ID.partyId()))
                .willReturn(badKey);
        given(library.extractPublicKey(AGGREGATION_KEY, B_NODE_PARTY_ID.partyId()))
                .willReturn(null);
        given(library.extractPublicKey(AGGREGATION_KEY, C_NODE_PARTY_ID.partyId()))
                .willReturn(goodKey);
        given(library.extractPublicKey(AGGREGATION_KEY, D_NODE_PARTY_ID.partyId()))
                .willReturn(goodKey);
        given(library.verifyPartial(BLOCK_HASH, signature, badKey)).willReturn(false);
        given(library.verifyPartial(BLOCK_HASH, signature, goodKey)).willReturn(true);
        final long cWeight = 1L;
        final long dWeight = 2L;
        given(library.extractTotalWeight(AGGREGATION_KEY)).willReturn(3 * (cWeight + dWeight));
        given(library.extractWeight(AGGREGATION_KEY, C_NODE_PARTY_ID.partyId())).willReturn(cWeight);
        given(library.extractWeight(AGGREGATION_KEY, D_NODE_PARTY_ID.partyId())).willReturn(dWeight);
        final Map<Long, BlsSignature> expectedSignatures = Map.of(
                C_NODE_PARTY_ID.nodeId(), signature,
                D_NODE_PARTY_ID.nodeId(), signature);
        final var aggregateSignature = Bytes.wrap("AS");
        given(library.signAggregate(AGGREGATION_KEY, expectedSignatures)).willReturn(aggregateSignature);

        subject.setConstruction(CONSTRUCTION);

        final var signing = subject.newSigning(BLOCK_HASH);
        final var future = signing.future();

        signing.incorporate(CONSTRUCTION.constructionId() + 1, 0L, signature);
        assertFalse(future.isDone());
        signing.incorporate(CONSTRUCTION.constructionId(), A_NODE_PARTY_ID.nodeId(), signature);
        assertFalse(future.isDone());
        signing.incorporate(CONSTRUCTION.constructionId(), B_NODE_PARTY_ID.nodeId(), signature);
        assertFalse(future.isDone());
        signing.incorporate(CONSTRUCTION.constructionId(), C_NODE_PARTY_ID.nodeId(), signature);
        assertFalse(future.isDone());
        signing.incorporate(CONSTRUCTION.constructionId(), D_NODE_PARTY_ID.nodeId(), signature);
        assertTrue(future.isDone());
        assertEquals(aggregateSignature, future.join());
    }
}
