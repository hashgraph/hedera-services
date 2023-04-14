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
import static com.hedera.node.app.service.mono.state.logic.RecordingStatusChangeListener.FINAL_TOPICS_ASSET;
import static com.hedera.node.app.service.mono.state.migration.RecordingMigrationManager.INITIAL_ACCOUNTS_ASSET;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.infra.InMemoryWritableStoreFactory;
import com.hedera.node.app.integration.infra.ReplayFacilityTransactionDispatcher;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.mono.state.logic.RecordingProcessLogic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static com.hedera.node.app.service.mono.stream.RecordingRecordStreamManager.RECORDS_ASSET;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplayFacilityTest {
    private ReplayFacilityComponent component;
    private ReplayAssetRecording assetRecording;
    private ReplayAdvancingConsensusNow consensusNow;
    private InMemoryWritableStoreFactory writableStoreFactory;
    private ReplayFacilityTransactionDispatcher transactionDispatcher;
    private Map<TransactionID, TransactionRecord> expectedRecords = new HashMap<>();

    @Test
    void reHandlesIdenticallyWithReplayFacilities() {
        setupReplayForScenario("hcs-crud");

        replayScenarioTransactions();

        assertFinalTopicsMatch();
    }

    private void replayScenarioTransactions() {
        final var recordedTxns = assetRecording.readJsonLinesFromReplayAsset(
                RecordingProcessLogic.REPLAY_TRANSACTIONS_ASSET, ConsensusTxn.class);
        for (final var consensusTxn : recordedTxns) {
            consensusNow.set(Instant.parse(consensusTxn.getConsensusTimestamp()));
            final Pair<HederaFunctionality, TransactionBody> parts;
            try {
                parts = partsOf(consensusTxn);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
            System.out.println("@ " + consensusNow.get() + " ➡️ " + parts);
            transactionDispatcher.dispatchHandle(parts.getLeft(), parts.getRight(), writableStoreFactory);
            // Assert the record customizations match the expected record
            final var expectedRecord = expectedRecords.get(parts.getRight().transactionID());
            transactionDispatcher.assertCustomizationsMatch(expectedRecord);
        }
    }

    private void assertFinalTopicsMatch() {
        final var expectedTopics = assetRecording.readPbjEncodingsFromReplayAsset(FINAL_TOPICS_ASSET, Topic.PROTOBUF);
        final var actualTopics = writableStoreFactory.getServiceStates()
                .get(ConsensusService.NAME).<EntityNum, Topic>get(ConsensusServiceImpl.TOPICS_KEY);
        assertEquals(expectedTopics.size(), (int) actualTopics.size(),
                "Expected " + expectedTopics.size() + " topics, but got " + actualTopics.size());
        expectedTopics.forEach(expectedTopic -> {
            final var actualTopic = actualTopics.get(EntityNum.fromLong(expectedTopic.topicNumber()));
            assertEquals(expectedTopic, actualTopic, "Expected " + expectedTopic + ", but got " + actualTopic);
        });
    }

    private void setupReplayForScenario(@NonNull final String recordingName) {
        component = DaggerReplayFacilityComponent.factory().create(recordingName);
        consensusNow = component.consensusNow();
        assetRecording = component.assetRecording();
        writableStoreFactory = component.writableStoreFactory();
        transactionDispatcher = component.transactionDispatcher();

        loadExpectedRecords();
        rebuildInitialAccounts();
    }

    private void loadExpectedRecords() {
        assetRecording.readPbjEncodingsFromReplayAsset(RECORDS_ASSET, TransactionRecord.PROTOBUF).forEach(pbjRecord ->
                expectedRecords.put(pbjRecord.transactionID(), pbjRecord));
    }

    private void rebuildInitialAccounts() {
        final var tokens = writableStoreFactory.getServiceStates()
                .get(TokenService.NAME).<EntityNum, Account>get(TokenServiceImpl.ACCOUNTS_KEY);
        assetRecording.readPbjEncodingsFromReplayAsset(INITIAL_ACCOUNTS_ASSET, Account.PROTOBUF).forEach(account ->
                tokens.put(EntityNum.fromLong(account.accountNumber()), account));
        ((MapWritableKVState<EntityNum, Account>) tokens).commit();
    }

    private Pair<HederaFunctionality, TransactionBody> partsOf(final ConsensusTxn consensusTxn)
            throws InvalidProtocolBufferException {
        final var serialized = Base64.getDecoder().decode(consensusTxn.getB64Transaction());
        final var accessor = SignedTxnAccessor.from(serialized);
        return Pair.of(toPbj(accessor.getFunction()), toPbj(accessor.getTxn()));
    }
}
