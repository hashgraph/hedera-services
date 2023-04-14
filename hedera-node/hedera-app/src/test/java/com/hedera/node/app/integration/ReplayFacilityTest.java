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

package com.hedera.node.app.integration;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.infra.InMemoryWritableStoreFactory;
import com.hedera.node.app.integration.infra.ReplayFacilityTransactionDispatcher;
import com.hedera.node.app.service.mono.state.logic.RecordingProcessLogic;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import java.time.Instant;
import java.util.Base64;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReplayFacilityTest {
    private ReplayFacilityComponent component;

    private ReplayAssetRecording assetRecording;
    private ReplayAdvancingConsensusNow consensusNow;
    private InMemoryWritableStoreFactory writableStoreFactory;
    private ReplayFacilityTransactionDispatcher transactionDispatcher;

    @BeforeEach
    void setUp() {
        component = DaggerReplayFacilityComponent.factory().create();
        consensusNow = component.consensusNow();
        assetRecording = component.assetRecording();
        writableStoreFactory = component.writableStoreFactory();
        transactionDispatcher = component.transactionDispatcher();
    }

    @Test
    void reHandlesIdenticallyWithReplayFacilities() throws InvalidProtocolBufferException {
        final var recordedTxns = assetRecording.readJsonLinesFromReplayAsset(
                RecordingProcessLogic.REPLAY_TRANSACTIONS_ASSET, ConsensusTxn.class);
        for (final var consensusTxn : recordedTxns) {
            consensusNow.set(Instant.parse(consensusTxn.getConsensusTimestamp()));
            final var parts = partsOf(consensusTxn);
            System.out.println(parts);
            transactionDispatcher.dispatchHandle(parts.getLeft(), parts.getRight(), writableStoreFactory);
        }
    }

    private Pair<HederaFunctionality, TransactionBody> partsOf(final ConsensusTxn consensusTxn)
            throws InvalidProtocolBufferException {
        final var serialized = Base64.getDecoder().decode(consensusTxn.getB64Transaction());
        final var accessor = SignedTxnAccessor.from(serialized);
        return Pair.of(toPbj(accessor.getFunction()), toPbj(accessor.getTxn()));
    }
}
