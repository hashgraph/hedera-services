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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.swirlds.platform.system.transaction.TransactionWrapperUtils.createAppPayloadWrapper;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.AbstractFakePlatform;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeConsensusEvent;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeEvent;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeRound;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An embedded Hedera node that handles transactions synchronously on ingest and thus
 * cannot be used in concurrent tests.
 */
public class RepeatableEmbeddedHedera extends AbstractEmbeddedHedera implements EmbeddedHedera {
    private static final Instant FIXED_POINT = Instant.parse("2024-06-24T12:05:41.487328Z");
    private static final Duration SIMULATED_ROUND_DURATION = Duration.ofSeconds(1);
    private final FakeTime time = new FakeTime(FIXED_POINT, Duration.ZERO);
    private final SynchronousFakePlatform platform;

    public RepeatableEmbeddedHedera(@NonNull final EmbeddedNode node) {
        super(node);
        platform = new SynchronousFakePlatform(defaultNodeId, addressBook, executorService);
    }

    @Override
    protected AbstractFakePlatform fakePlatform() {
        return platform;
    }

    @Override
    public Instant now() {
        return time.now();
    }

    @Override
    public void tick(@NonNull Duration duration) {
        time.tick(duration);
    }

    @Override
    public TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final AccountID nodeAccountId,
            @NonNull final SemanticVersion semanticVersion) {
        requireNonNull(transaction);
        requireNonNull(nodeAccountId);
        requireNonNull(semanticVersion);
        var response = OK_RESPONSE;
        final Bytes payload = Bytes.wrap(transaction.toByteArray());
        if (defaultNodeAccountId.equals(nodeAccountId)) {
            final var responseBuffer = BufferedData.allocate(MAX_PLATFORM_TXN_SIZE);
            hedera.ingestWorkflow().submitTransaction(payload, responseBuffer);
            response = parseTransactionResponse(responseBuffer);
        } else {
            final var nodeId = nodeIds.getOrDefault(nodeAccountId, MISSING_NODE_ID);
            warnOfSkippedIngestChecks(nodeAccountId, nodeId);
            platform.lastCreatedEvent =
                    new FakeEvent(nodeId, time.now(), semanticVersion, createAppPayloadWrapper(payload));
        }
        if (response.getNodeTransactionPrecheckCode() == OK) {
            handleNextRound(false);
            // If handling this transaction scheduled TSS work, do it synchronously as well
            while (tssBaseService.hasTssSubmission()) {
                platform.lastCreatedEvent = null;
                tssBaseService.executeNextTssSubmission();
                if (platform.lastCreatedEvent != null) {
                    handleNextRound(true);
                }
            }
        }
        return response;
    }

    @Override
    protected long validStartOffsetSecs() {
        // We handle each transaction in a round starting in the next second of fake consensus time, so
        // we don't need any offset here; this simplifies tests that validate purging expired receipts
        return 0L;
    }

    /**
     * Returns the last consensus round number.
     */
    public long lastRoundNo() {
        return platform.lastRoundNo();
    }

    private void handleNextRound(boolean skipsSignatureTxn) {
        hedera.onPreHandle(platform.lastCreatedEvent, state);
        if (skipsSignatureTxn && platform.lastCreatedEvent.function() == HederaFunctionality.TSS_SHARE_SIGNATURE) {
            return;
        }
        final var round = platform.nextConsensusRound();
        // Handle each transaction in own round
        hedera.handleWorkflow().handleRound(state, round);
        hedera.onSealConsensusRound(round, state);
        notifyBlockStreamManagerIfEnabled(round.getRoundNum());
    }

    private class SynchronousFakePlatform extends AbstractFakePlatform implements Platform {
        private FakeEvent lastCreatedEvent;

        public SynchronousFakePlatform(
                @NonNull NodeId selfId,
                @NonNull AddressBook addressBook,
                @NonNull ScheduledExecutorService executorService) {
            super(selfId, addressBook, executorService);
        }

        @Override
        public boolean createTransaction(@NonNull final byte[] transaction) {
            lastCreatedEvent = new FakeEvent(
                    defaultNodeId, time.now(), version.getPbjSemanticVersion(), createAppPayloadWrapper(transaction));
            return true;
        }

        @Override
        public void start() {
            // No-op
        }

        private Round nextConsensusRound() {
            time.tick(SIMULATED_ROUND_DURATION);
            final var firstRoundTime = time.now();
            final var consensusEvents = List.<ConsensusEvent>of(new FakeConsensusEvent(
                    requireNonNull(lastCreatedEvent),
                    consensusOrder.getAndIncrement(),
                    firstRoundTime,
                    lastCreatedEvent.getSoftwareVersion()));
            return new FakeRound(roundNo.getAndIncrement(), requireNonNull(roster), consensusEvents);
        }
    }
}
