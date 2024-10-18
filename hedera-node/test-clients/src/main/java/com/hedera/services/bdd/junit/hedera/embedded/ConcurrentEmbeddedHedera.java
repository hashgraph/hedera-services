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

import static com.swirlds.platform.system.transaction.TransactionWrapperUtils.createAppPayloadWrapper;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.events.ConsensusEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An embedded Hedera node that can be used in concurrent tests.
 */
class ConcurrentEmbeddedHedera extends AbstractEmbeddedHedera implements EmbeddedHedera {
    private static final Logger log = LogManager.getLogger(ConcurrentEmbeddedHedera.class);
    private static final Duration SIMULATED_ROUND_DURATION = Duration.ofMillis(1);

    private final ConcurrentFakePlatform platform;

    public ConcurrentEmbeddedHedera(@NonNull final EmbeddedNode node) {
        super(node);
        platform = new ConcurrentFakePlatform(executorService);
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void tick(@NonNull final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final AccountID nodeAccountId,
            @NonNull final SemanticVersion semanticVersion) {
        requireNonNull(transaction);
        requireNonNull(nodeAccountId);
        requireNonNull(semanticVersion);
        if (defaultNodeAccountId.equals(nodeAccountId)) {
            final var responseBuffer = BufferedData.allocate(MAX_PLATFORM_TXN_SIZE);
            hedera.ingestWorkflow().submitTransaction(Bytes.wrap(transaction.toByteArray()), responseBuffer);
            return parseTransactionResponse(responseBuffer);
        } else {
            final var nodeId = nodeIds.getOrDefault(nodeAccountId, MISSING_NODE_ID);
            warnOfSkippedIngestChecks(nodeAccountId, nodeId);
            platform.ingestQueue()
                    .add(new FakeEvent(
                            nodeId, now(), semanticVersion, createAppPayloadWrapper(transaction.toByteArray())));
            return OK_RESPONSE;
        }
    }

    @Override
    protected AbstractFakePlatform fakePlatform() {
        return platform;
    }

    private class ConcurrentFakePlatform extends AbstractFakePlatform implements Platform {
        private static final int MIN_CAPACITY = 5_000;
        private static final Duration WALL_CLOCK_ROUND_DURATION = Duration.ofMillis(1);

        private final List<FakeEvent> prehandledEvents = new ArrayList<>();
        private final BlockingQueue<FakeEvent> queue = new ArrayBlockingQueue<>(MIN_CAPACITY);
        private final ScheduledExecutorService executorService;

        public ConcurrentFakePlatform(@NonNull final ScheduledExecutorService executorService) {
            super(defaultNodeId, addressBook, requireNonNull(executorService));
            this.executorService = executorService;
        }

        public BlockingQueue<FakeEvent> ingestQueue() {
            return queue;
        }

        @Override
        public void start() {
            executorService.scheduleWithFixedDelay(
                    this::handleTransactions, 0, WALL_CLOCK_ROUND_DURATION.toMillis(), MILLISECONDS);
        }

        @Override
        public boolean createTransaction(@NonNull byte[] transaction) {
            return queue.add(new FakeEvent(
                    defaultNodeId, now(), version.getPbjSemanticVersion(), createAppPayloadWrapper(transaction)));
        }

        /**
         * Simulates a round of events coming to consensus and being handled by the Hedera node.
         *
         * <p>We advance consensus time by 1 second in fake time for each round, unless some other
         * event like a synthetic "sleep" has already advanced the time.
         */
        private void handleTransactions() {
            try {
                // Put all pre-handled events that were last drained from the queue into a round
                if (!prehandledEvents.isEmpty()) {
                    // Advance time only if something reached consensus
                    tick(SIMULATED_ROUND_DURATION);
                    final var firstRoundTime = now();
                    // Note we are only putting one transaction in each event
                    final var consensusEvents = IntStream.range(0, prehandledEvents.size())
                            .<ConsensusEvent>mapToObj(i -> {
                                final var event = prehandledEvents.get(i);
                                return new FakeConsensusEvent(
                                        event,
                                        consensusOrder.getAndIncrement(),
                                        firstRoundTime.plusNanos(i * NANOS_BETWEEN_CONS_EVENTS),
                                        event.getSoftwareVersion());
                            })
                            .toList();
                    final var round = new FakeRound(roundNo.getAndIncrement(), addressBook, consensusEvents);
                    hedera.handleWorkflow().handleRound(state, round);
                    hedera.onSealConsensusRound(round, state);
                    notifyBlockStreamManagerIfEnabled(round.getRoundNum());
                    prehandledEvents.clear();
                }
                // Now drain all events that will go in the next round and pre-handle them
                final List<FakeEvent> newEvents = new ArrayList<>();
                queue.drainTo(newEvents);
                newEvents.forEach(event -> hedera.onPreHandle(event, state));
                prehandledEvents.addAll(newEvents);
            } catch (Throwable t) {
                log.error("Error handling transactions", t);
            }
        }
    }
}
