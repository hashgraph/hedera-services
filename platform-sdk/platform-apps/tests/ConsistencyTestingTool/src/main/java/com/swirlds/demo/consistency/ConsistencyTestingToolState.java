/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Consistency Testing Tool
 */
@ConstructableIgnored
public class ConsistencyTestingToolState extends PlatformMerkleStateRoot {
    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);
    private static final long CLASS_ID = 0xda03bb07eb897d82L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Nodes at indices 0, 1, and 2 are used by the PlatformState, RosterMap, and RosterState.
    private static final int STATE_LONG_INDEX = 3;
    private static final int ROUND_HANDLED_INDEX = 4;

    /**
     * The true "state" of this app. This long value is updated with every transaction, and with every round.
     * <p>
     * Affects the hash of this node.
     */
    private long stateLong = 0;

    /**
     * The number of rounds handled by this app. Is incremented each time
     * {@link ConsistencyTestingToolStateLifecycles#onHandleConsensusRound(Round, ConsistencyTestingToolState, Consumer < ScopedSystemTransaction < StateSignatureTransaction >>)} is called. Note that this may not actually equal the round
     * number, since we don't call {@link ConsistencyTestingToolStateLifecycles#onHandleConsensusRound(Round, ConsistencyTestingToolState, Consumer<ScopedSystemTransaction<StateSignatureTransaction>>)} for rounds with no events.
     *
     * <p>
     * Affects the hash of this node.
     */
    private long roundsHandled = 0;

    /**
     * The history of transactions that have been handled by this app.
     * <p>
     * A deep copy of this object is NOT created when this state is copied. This object does not affect the hash of this
     * node.
     */
    private final TransactionHandlingHistory transactionHandlingHistory;

    /**
     * The set of transactions that have been preconsensus-handled by this app, but haven't yet been
     * postconsensus-handled. This is used to ensure that transactions are prehandled exactly 1 time, prior to
     * posthandling.
     * <p>
     * Does not affect the hash of this node.
     */
    private final Set<Long> transactionsAwaitingPostHandle;

    /**
     * Constructor
     */
    public ConsistencyTestingToolState(@NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(versionFactory);
        transactionHandlingHistory = new TransactionHandlingHistory();
        transactionsAwaitingPostHandle = ConcurrentHashMap.newKeySet();
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Initialize the state
     */
    void initState(Path logFilePath) {
        final StringLeaf stateLongLeaf = getChild(STATE_LONG_INDEX);
        if (stateLongLeaf != null && stateLongLeaf.getLabel() != null) {
            this.stateLong = Long.parseLong(stateLongLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with state long {}.", stateLong);
        }
        final StringLeaf roundsHandledLeaf = getChild(ROUND_HANDLED_INDEX);
        if (roundsHandledLeaf != null && roundsHandledLeaf.getLabel() != null) {
            this.roundsHandled = Long.parseLong(roundsHandledLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with {} rounds handled.", roundsHandled);
        }
        transactionHandlingHistory.init(logFilePath);
    }

    /**
     * @return the number of rounds handled
     */
    long getRoundsHandled() {
        return roundsHandled;
    }

    /**
     * Increment the number of rounds handled
     */
    void incrementRoundsHandled() {
        roundsHandled++;
        setChild(ROUND_HANDLED_INDEX, new StringLeaf(Long.toString(roundsHandled)));
    }

    /**
     * @return the state represented by a long
     */
    long getStateLong() {
        return stateLong;
    }

    /**
     * Sets the state
     * @param stateLong state represtented by a long
     */
    void setStateLong(final long stateLong) {
        this.stateLong = stateLong;
        setChild(STATE_LONG_INDEX, new StringLeaf(Long.toString(stateLong)));
    }

    /**
     * Copy constructor
     *
     * @param that the state to copy
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(Objects.requireNonNull(that));
        this.stateLong = that.stateLong;
        this.roundsHandled = that.roundsHandled;
        this.transactionHandlingHistory = that.transactionHandlingHistory;
        this.transactionsAwaitingPostHandle = that.transactionsAwaitingPostHandle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }

    private void processRound(Round round) {
        stateLong = NonCryptographicHashing.hash64(stateLong, round.getRoundNum());
        transactionHandlingHistory.processRound(ConsistencyTestingToolRound.fromRound(round, stateLong));
        setChild(STATE_LONG_INDEX, new StringLeaf(Long.toString(stateLong)));
    }

    void processTransactions(Round round,
                             @NonNull final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        incrementRoundsHandled();

        round.forEachEventTransaction((ev, tx) -> {
            if (isSystemTransaction(tx)) {
                consumeSystemTransaction(tx, ev, stateSignatureTransaction);
            } else {
                applyTransactionToState(tx);
            }
        });

        round.forEachTransaction(this::applyTransactionToState);
        processRound(round);
    }

    void processPrehandle(long transactionContents) {
        if (!transactionsAwaitingPostHandle.add(transactionContents)) {
            logger.error(EXCEPTION.getMarker(), "Transaction {} was prehandled more than once.", transactionContents);
        }
    }

    /**
     * Sets the new {@link ConsistencyTestingToolState#stateLong} to the non-cryptographic hash of the existing state, and the contents of the
     * transaction being handled
     *
     * @param transaction the transaction to apply to the state
     */
    private void applyTransactionToState(final @NonNull ConsensusTransaction transaction) {
        Objects.requireNonNull(transaction);
        if (transaction.isSystem()) {
            return;
        }

        final long transactionContents =
                byteArrayToLong(transaction.getApplicationTransaction().toByteArray(), 0);

        if (!transactionsAwaitingPostHandle.remove(transactionContents)) {
            logger.error(EXCEPTION.getMarker(), "Transaction {} was not prehandled.", transactionContents);
        }

        stateLong = NonCryptographicHashing.hash64(stateLong, transactionContents);
        setChild(STATE_LONG_INDEX, new StringLeaf(Long.toString(stateLong)));
    }

    /**
     * Determines if the given transaction is a system transaction for this app.
     *
     * @param transaction the transaction to check
     * @return true if the transaction is a system transaction, false otherwise
     */
    boolean isSystemTransaction(final @NonNull Transaction transaction) {
        return transaction.getApplicationTransaction().length() > 8;
    }

    void consumeSystemTransaction(
            final @NonNull Transaction transaction,
            final @NonNull Event event,
            final @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                            stateSignatureTransactionCallback) {
        try {
            final var stateSignatureTransaction =
                    StateSignatureTransaction.PROTOBUF.parse(transaction.getApplicationTransaction());
            stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                    event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
        } catch (final ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized ConsistencyTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new ConsistencyTestingToolState(this);
    }
}
