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

package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles lifecycle events for the {@link ConsistencyTestingToolState}
 */
public class ConsistencyTestingToolStateLifecycles implements StateLifecycles<ConsistencyTestingToolState> {

    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);

    /**
     * The history of transactions that have been handled by this app.
     * <p>
     * A deep copy of this object is NOT created when this state is copied. This object does not affect the hash of this
     * node.
     */
    private final TransactionHandlingHistory transactionHandlingHistory;

    /**
     * If not zero, and we are handling the first round after genesis, configure a freeze this duration later.
     * <p>
     * Does not affect the hash of this node (although actions may be taken based on this info that DO affect the
     * hash).
     */
    private Duration freezeAfterGenesis = null;

    /**
     * The set of transactions that have been preconsensus-handled by this app, but haven't yet been
     * postconsensus-handled. This is used to ensure that transactions are prehandled exactly 1 time, prior to
     * posthandling.
     * <p>
     * Does not affect the hash of this node.
     */
    private final Set<Long> transactionsAwaitingPostHandle = ConcurrentHashMap.newKeySet();

    public ConsistencyTestingToolStateLifecycles() {
        this.transactionHandlingHistory = new TransactionHandlingHistory();
    }

    @Override
    public void onStateInitialized(
            @NonNull ConsistencyTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        requireNonNull(platform);
        requireNonNull(trigger);

        final StateCommonConfig stateConfig =
                platform.getContext().getConfiguration().getConfigData(StateCommonConfig.class);
        final ConsistencyTestingToolConfig testingToolConfig =
                platform.getContext().getConfiguration().getConfigData(ConsistencyTestingToolConfig.class);

        final Path logFileDirectory = stateConfig
                .savedStateDirectory()
                .resolve(testingToolConfig.logfileDirectory())
                .resolve(Long.toString(platform.getSelfId().id()));
        try {
            Files.createDirectories(logFileDirectory);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to set up file system for consistency data", e);
        }
        final Path logFilePath = logFileDirectory.resolve("ConsistencyTestLog.csv");

        this.freezeAfterGenesis = testingToolConfig.freezeAfterGenesis();

        state.initState();

        transactionHandlingHistory.init(logFilePath);
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);
    }

    /**
     * Modifies the state based on each transaction in the round
     * <p>
     * Writes the round and its contents to a log on disk
     */
    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull ConsistencyTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        requireNonNull(round);
        requireNonNull(state);
        PlatformStateModifier platformState = state.getWritablePlatformState();
        requireNonNull(platformState);

        if (state.getRoundsHandled() == 0 && !freezeAfterGenesis.equals(Duration.ZERO)) {
            // This is the first round after genesis.
            logger.info(
                    STARTUP.getMarker(),
                    "Setting freeze time to {} seconds after genesis.",
                    freezeAfterGenesis.getSeconds());
            platformState.setFreezeTime(round.getConsensusTimestamp().plus(freezeAfterGenesis));
        }

        round.forEachTransaction(v -> applyTransactionToState(v, state));

        state.incrementRoundsHandled();
        final long stateLong = NonCryptographicHashing.hash64(state.getStateLong(), round.getRoundNum());
        transactionHandlingHistory.processRound(ConsistencyTestingToolRound.fromRound(round, stateLong));
        state.setStateLong(stateLong);
    }

    /**
     * Sets the new {@link ConsistencyTestingToolState#stateLong} to the non-cryptographic hash of the existing state, and the contents of the
     * transaction being handled
     *
     * @param transaction the transaction to apply to the state
     */
    private void applyTransactionToState(
            final @NonNull ConsensusTransaction transaction, @NonNull ConsistencyTestingToolState state) {
        Objects.requireNonNull(transaction);
        if (transaction.isSystem()) {
            return;
        }

        final long transactionContents =
                byteArrayToLong(transaction.getApplicationTransaction().toByteArray(), 0);

        if (!transactionsAwaitingPostHandle.remove(transactionContents)) {
            logger.error(EXCEPTION.getMarker(), "Transaction {} was not prehandled.", transactionContents);
        }

        state.setStateLong(NonCryptographicHashing.hash64(state.getStateLong(), transactionContents));
    }

    /**
     * Keeps track of which transactions have been prehandled.
     */
    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull ConsistencyTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        event.forEachTransaction(transaction -> {
            if (transaction.isSystem()) {
                return;
            }
            final long transactionContents =
                    byteArrayToLong(transaction.getApplicationTransaction().toByteArray(), 0);

            if (!transactionsAwaitingPostHandle.add(transactionContents)) {
                logger.error(
                        EXCEPTION.getMarker(), "Transaction {} was prehandled more than once.", transactionContents);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull ConsistencyTestingToolState state) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateWeight(
            @NonNull ConsistencyTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNewRecoveredState(@NonNull ConsistencyTestingToolState recoveredState) {
        // no-op
    }
}
