/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.services.utils.Units.MIN_TRANS_TIMESTAMP_INCR_NANOS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a tracker of the usable consensus time space during a {@link
 * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call.
 */
@Singleton
public class ConsensusTimeTracker {
    private static final Logger log = LogManager.getLogger(ConsensusTimeTracker.class);

    @VisibleForTesting
    static final long DEFAULT_NANOS_PER_INCORPORATE_CALL = MIN_TRANS_TIMESTAMP_INCR_NANOS;

    private final GlobalDynamicProperties properties;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final long nanosBetweenIncorporateCalls;

    private Instant minConsensusTime;
    private long currentTxnMinTime;
    private long currentTxnTime;
    private long currentTxnMaxTime;
    private long maxConsensusTime;

    private long followingRecordsCount;

    private boolean firstUsed;

    /**
     * The max number of records that can be placed before transactions in a {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call.
     */
    private long maxPrecedingRecords;
    /**
     * There is a special case. When the system first starts up, there are migrations that can
     * process that need unlimited preceding records. See {@link
     * com.hedera.services.state.migration.MigrationRecordsManager}.
     *
     * <p>unlimitedPreceding should only ever true on the first transaction in an {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call.
     */
    private boolean unlimitedPreceding;
    /**
     * The max number of records that can be placed after a transaction in a {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call
     */
    private long maxFollowingRecords;

    @Inject
    public ConsensusTimeTracker(
            final GlobalDynamicProperties properties,
            final Supplier<MerkleNetworkContext> networkCtx) {
        this(properties, networkCtx, DEFAULT_NANOS_PER_INCORPORATE_CALL);
    }

    @VisibleForTesting
    ConsensusTimeTracker(
            final GlobalDynamicProperties properties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final long nanosBetweenIncorporateCalls) {
        maxPrecedingRecords = properties.maxPrecedingRecords();
        maxFollowingRecords = properties.maxFollowingRecords();
        this.nanosBetweenIncorporateCalls = nanosBetweenIncorporateCalls;
        if (nanosBetweenIncorporateCalls < (maxFollowingRecords + maxPrecedingRecords + 10)) {
            throw new IllegalArgumentException(
                    "TransactionHandler.MIN_TRANS_TIMESTAMP_INCR_NANOS is too small!");
        }
        this.properties = properties;
        this.networkCtx = networkCtx;
    }

    @VisibleForTesting
    ConsensusTimeTracker(ConsensusTimeTracker toCopy) {
        this(toCopy.properties, toCopy.networkCtx);
        minConsensusTime = toCopy.minConsensusTime;
        currentTxnMinTime = toCopy.currentTxnMinTime;
        currentTxnTime = toCopy.currentTxnTime;
        currentTxnMaxTime = toCopy.currentTxnMaxTime;
        maxConsensusTime = toCopy.maxConsensusTime;
        followingRecordsCount = toCopy.followingRecordsCount;
        firstUsed = toCopy.firstUsed;
        maxFollowingRecords = toCopy.maxFollowingRecords;
        maxPrecedingRecords = toCopy.maxPrecedingRecords;
        unlimitedPreceding = toCopy.unlimitedPreceding;
    }

    /**
     * @param count the actual number of records that were placed following the consensusTime of the
     *     current transaction.
     */
    public void setActualFollowingRecordsCount(final long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, it was " + count);
        }
        followingRecordsCount = count;
    }

    /**
     * @return true if firstTransactionTime() cannot be used again in the current {@link
     *     com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call
     */
    public boolean isFirstUsed() {
        return firstUsed;
    }

    /**
     * @return true if we allow an unlimited number of preceding records for the current
     *     transaction.
     */
    public boolean unlimitedPreceding() {
        return unlimitedPreceding;
    }

    /**
     * Resets this tracker, should be called at the beginning of {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn}
     *
     * @param consensusTime the consensusTime passed to incorporateConsensusTxn
     */
    public void reset(final Instant consensusTime) {
        maxPrecedingRecords = properties.maxPrecedingRecords();
        maxFollowingRecords = properties.maxFollowingRecords();
        unlimitedPreceding = !networkCtx.get().areMigrationRecordsStreamed();

        minConsensusTime = consensusTime;
        currentTxnMinTime = 0;
        currentTxnTime = currentTxnMinTime + maxPrecedingRecords;
        currentTxnMaxTime = currentTxnTime + maxFollowingRecords;
        followingRecordsCount = maxFollowingRecords;
        firstUsed = false;
        maxConsensusTime = currentTxnMinTime + (nanosBetweenIncorporateCalls - 1L);
    }

    /**
     * The first transaction in a {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call can have
     * more "preceding" transactions than normal, this method returns the first consensus time. If
     * it has already been used, and exception is thrown. See {@link #isFirstUsed()}.
     *
     * @return the consensus time to use for the first transaction in a {@link
     *     com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call.
     */
    public Instant firstTransactionTime() {

        if (firstUsed) {
            throw new IllegalStateException(
                    "firstTransactionTime can only be used once, before nextTransactionTime is"
                            + " called, per incorporateConsensusTxn call!");
        }

        firstUsed = true;

        return minConsensusTime.plusNanos(currentTxnTime);
    }

    /**
     * Moves the tracker forward to the next usable consensus time for a full transaction and
     * returns it. Will throw an exception if there are no more consensus times available. Use
     * {@link #hasMoreTransactionTime} before calling this.
     *
     * <p>This _should not_ be called anywhere that could possibly be called during the handling of
     * a normal transaction. It resets the tracker to the "next" state. It should really only be
     * called by {@link
     * com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn}.
     *
     * <p>Use {@link TxnAwareRecordsHistorian} for getting record consensus times that are
     * associated with a transaction.
     *
     * @param canTriggerTxn true if the next transaction is allowed to trigger transactions
     * @return the consensus time to use.
     */
    public Instant nextTransactionTime(boolean canTriggerTxn) {
        return nextTime(canTriggerTxn, maxPrecedingRecords, maxFollowingRecords);
    }

    /**
     * @param canTriggerTxn true if the next transaction is going to be allowed to trigger
     *     transactions
     * @return true if there are more full transaction times available.
     */
    public boolean hasMoreTransactionTime(boolean canTriggerTxn) {
        return hasMoreTime(canTriggerTxn, maxPrecedingRecords, maxFollowingRecords);
    }

    /**
     * Moves the tracker forward to the next usable consensus time for a "standalone" record. A
     * "standalone" record is a record that is not associated with a transaction and will not
     * attempt to create preceding or following records.
     *
     * <p>This _should not_ be called anywhere that could possibly be called during the handling of
     * a normal transaction. It resets the tracker to the "next" state.
     *
     * <p>Use {@link TxnAwareRecordsHistorian} for getting record consensus times that are
     * associated with a transaction.
     *
     * @return the next "standalone" consensus time.
     */
    public Instant nextStandaloneRecordTime() {
        return nextTime(false, 0, 0);
    }

    /**
     * @return true if there are more "standalone" record times available.
     */
    public boolean hasMoreStandaloneRecordTime() {
        return hasMoreTime(false, 0, 0);
    }

    /**
     * @param offset an offset from the current transaction consensus time
     * @return true if the current transaction consensus time plus offset should be allowed as a
     *     record consensus time in a "following" record.
     */
    public boolean isAllowableFollowingOffset(final long offset) {
        long time = currentTxnTime + offset;

        return (time > currentTxnTime) && (time <= currentTxnMaxTime);
    }

    /**
     * @param offset an offset from the current transaction consensus time
     * @return true if the current transaction consensus time minus offset should be allowed as a
     *     record consensus time in a "preceding" record.
     */
    public boolean isAllowablePrecedingOffset(final long offset) {

        long time = currentTxnTime - offset;

        if (unlimitedPreceding()) {
            return time < currentTxnTime;
        }

        return (time < currentTxnTime) && (time >= currentTxnMinTime);
    }

    private Instant nextTime(
            final boolean canTriggerTxn, final long maxPreceding, final long maxFollowing) {

        if (!hasMoreTime(canTriggerTxn, maxPreceding, maxFollowing)) {
            log.error("Cannot get more transaction times! {}", this);
            throw new IllegalStateException("Cannot get more transaction times!");
        }

        firstUsed = true;
        unlimitedPreceding = false;

        currentTxnMinTime = currentTxnTime + (followingRecordsCount + 1);

        currentTxnTime = currentTxnMinTime + maxPreceding;

        currentTxnMaxTime = currentTxnTime + maxFollowing;

        followingRecordsCount = maxFollowing;

        return minConsensusTime.plusNanos(currentTxnTime);
    }

    private boolean hasMoreTime(
            boolean canTriggerTxn, final long maxPreceding, final long maxFollowing) {
        long next = currentTxnTime + followingRecordsCount;

        if (next > currentTxnMaxTime) {
            log.warn("Used more record slots than allowed per transaction! {}", this);
        }

        if (next > maxConsensusTime) {
            log.error("Exceeded the max nanos per handleTransaction() call! {}", this);
            throw new IllegalStateException("Exceeded the max nanos per handleTransaction() call!");
        }

        if (canTriggerTxn) {
            return (next + ((1L + maxPreceding + maxFollowing) * 2L)) <= maxConsensusTime;
        } else {
            return (next + (1L + maxPreceding + maxFollowing)) <= maxConsensusTime;
        }
    }

    @Override
    public String toString() {
        return "ConsensusTimeTracker{"
                + "nanosBetweenIncorporateCalls="
                + nanosBetweenIncorporateCalls
                + ", minConsensusTime="
                + minConsensusTime
                + ", currentTxnMinTime="
                + currentTxnMinTime
                + ", currentTxnTime="
                + currentTxnTime
                + ", currentTxnMaxTime="
                + currentTxnMaxTime
                + ", maxConsensusTime="
                + maxConsensusTime
                + ", followingRecordsCount="
                + followingRecordsCount
                + ", firstUsed="
                + firstUsed
                + ", maxFollowingRecords="
                + maxFollowingRecords
                + ", maxPrecedingRecords="
                + maxPrecedingRecords
                + '}';
    }

    @VisibleForTesting
    Instant getMinConsensusTime() {
        return minConsensusTime;
    }

    @VisibleForTesting
    Instant getCurrentTxnMinTime() {
        return minConsensusTime.plusNanos(currentTxnMinTime);
    }

    @VisibleForTesting
    Instant getCurrentTxnTime() {
        return minConsensusTime.plusNanos(currentTxnTime);
    }

    @VisibleForTesting
    Instant getCurrentTxnMaxTime() {
        return minConsensusTime.plusNanos(currentTxnMaxTime);
    }

    @VisibleForTesting
    Instant getMaxConsensusTime() {
        return minConsensusTime.plusNanos(maxConsensusTime);
    }

    @VisibleForTesting
    long getFollowingRecordsCount() {
        return followingRecordsCount;
    }

    @VisibleForTesting
    long getMaxPrecedingRecords() {
        return maxPrecedingRecords;
    }

    @VisibleForTesting
    long getMaxFollowingRecords() {
        return maxFollowingRecords;
    }
}
