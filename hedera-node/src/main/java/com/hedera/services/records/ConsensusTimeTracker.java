package com.hedera.services.records;
/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.platform.state.TransactionHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a tracker of the usable consensus time space during a
 * {@link com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn} call.
 */
@Singleton
public class ConsensusTimeTracker {
	private static final Logger log = LogManager.getLogger(ConsensusTimeTracker.class);

	@VisibleForTesting
	static final long DEFAULT_NANOS_PER_ROUND = TransactionHandler.MIN_TRANS_TIMESTAMP_INCR_NANOS;

	/**
	 * The max number of records that can be placed before the first transaction in a round. We need this because
	 * the first transaction might have more than a normal number of proceeding records.
	 * For example {@link com.hedera.services.state.migration.MigrationRecordsManager}
	 */
	public static final long MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND = 10;

	/**
	 * The max number of records that can be placed before subsequent transactions in a round.
	 */
	public static final long MAX_PRECEDING_RECORDS_REMAINING_TXN = 3;
	/**
	 * The max number of records that can be placed after a transaction.
	 */
	public static final long MAX_FOLLOWING_RECORDS = 10;

	private final long nanosPerRound;

	private Instant roundStartConsensusTime;
	private Instant currentTxnMinTime;
	private Instant currentTxnTime;
	private Instant currentTxnMaxTime;
	private Instant maxConsensusTime;

	private long followingRecordsCount;

	private boolean firstUsed;

	@Inject
	public ConsensusTimeTracker() {
		this(DEFAULT_NANOS_PER_ROUND);
	}

	@VisibleForTesting
	ConsensusTimeTracker(final long nanosPerRound) {
		this.nanosPerRound = nanosPerRound;
		if (nanosPerRound <
				(MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND + MAX_FOLLOWING_RECORDS
						+ MAX_PRECEDING_RECORDS_REMAINING_TXN + 10)) {
			throw new IllegalArgumentException("TransactionHandler.MIN_TRANS_TIMESTAMP_INCR_NANOS is too small!");
		}
	}

	@VisibleForTesting
	ConsensusTimeTracker(ConsensusTimeTracker toCopy) {
		this();
		roundStartConsensusTime = toCopy.roundStartConsensusTime;
		currentTxnMinTime = toCopy.currentTxnMinTime;
		currentTxnTime = toCopy.currentTxnTime;
		currentTxnMaxTime = toCopy.currentTxnMaxTime;
		maxConsensusTime = toCopy.maxConsensusTime;
		followingRecordsCount = toCopy.followingRecordsCount;
		firstUsed = toCopy.firstUsed;
	}

	/**
	 * @param count the actual number of records that were placed following the consensusTime of the current transaction.
	 */
	public void setActualFollowingRecordsCount(final long count) {
		if (count < 0) {
			throw new IllegalArgumentException("count must be >= 0, it was " + count);
		}
		followingRecordsCount = count;
	}

	/**
	 * @return true if firstTransactionTime() cannot be used again in this round
	 */
	public boolean isFirstUsed() {
		return firstUsed;
	}

	/**
	 * Resets this tracker, should be called at the beginning of
	 * {@link com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn}
	 * @param consensusTime the consensusTime passed to incorporateConsensusTxn
	 */
	public void reset(final Instant consensusTime) {
		roundStartConsensusTime = consensusTime;
		currentTxnMinTime = roundStartConsensusTime;
		currentTxnTime = roundStartConsensusTime.plusNanos(MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND);
		currentTxnMaxTime = currentTxnTime.plusNanos(MAX_FOLLOWING_RECORDS);
		followingRecordsCount = MAX_FOLLOWING_RECORDS;
		firstUsed = false;
		maxConsensusTime = consensusTime.plusNanos(DEFAULT_NANOS_PER_ROUND - 1);
	}

	/**
	 * The first transaction in a round can have more "preceding" transactions than normal, this method
	 * returns the first consensus time, if it hasn't been used yet.
	 *
	 * @return the consensus time to use for the first transaction in a round.
	 */
	public Instant firstTransactionTime() {

		if (firstUsed) {
			throw new IllegalStateException(
					"firstTransactionTime can only be used once, before nextTransactionTime is called, per round!");
		}

		firstUsed = true;

		return currentTxnTime;
	}

	/**
	 * Moves the tracker forward to the next usable consensus time for a full transaction and returns it. Will throw
	 * an exception if there are no more consensus times available. Use {@link #hasMoreTransactionTime} before
	 * calling this.
	 *
	 * This _should not_ be called anywhere that could possibly be called during the handling of a normal transaction.
	 * It resets the tracker to the "next" state. It should really only be called by
	 * {@link com.hedera.services.state.logic.StandardProcessLogic#incorporateConsensusTxn}.
	 *
	 * Use {@link TxnAwareRecordsHistorian} for getting record consensus times that are associated with a transaction.
	 *
	 * @param canTriggerTxn true if the next transaction is allowed to trigger transactions
	 * @return the consensus time to use.
	 */
	public Instant nextTransactionTime(boolean canTriggerTxn) {
		return nextTime(canTriggerTxn, MAX_PRECEDING_RECORDS_REMAINING_TXN, MAX_FOLLOWING_RECORDS);
	}

	/**
	 * @param canTriggerTxn true if the next transaction is going to be allowed to trigger transactions
	 * @return true if there are more full transaction times available.
	 */
	public boolean hasMoreTransactionTime(boolean canTriggerTxn) {
		return hasMoreTime(canTriggerTxn, MAX_PRECEDING_RECORDS_REMAINING_TXN, MAX_FOLLOWING_RECORDS);
	}

	/**
	 * Moves the tracker forward to the next usable consensus time for a "standalone" record. A "standalone" record
	 * is a record that is not associated with a transaction and will not attempt to create preceding or following
	 * records.
	 *
	 * This _should not_ be called anywhere that could possibly be called during the handling of a normal transaction.
	 * It resets the tracker to the "next" state.
	 *
	 * Use {@link TxnAwareRecordsHistorian} for getting record consensus times that are associated with a transaction.
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
	 * @param time the consensus time of a record
	 * @return true if time should be allowed as a record consensus time in a "following" record.
	 */
	public boolean isAllowableFollowingTime(final Instant time) {
		return time.isAfter(currentTxnTime) && (time.isBefore(currentTxnMaxTime) || time.equals(currentTxnMaxTime));
	}

	/**
	 * @param offset an offset from the current transaction consensus time
	 * @return true if the current transaction consensus time plus offset should be allowed as a
	 *         record consensus time in a "following" record.
	 */
	public boolean isAllowableFollowingOffset(final long offset) {
		return isAllowableFollowingTime(currentTxnTime.plusNanos(offset));
	}

	/**
	 * @param time the consensus time of a record
	 * @return true if time should be allowed as a record consensus time in a "preceding" record.
	 */
	public boolean isAllowablePrecedingTime(final Instant time) {
		return time.isBefore(currentTxnTime) && (time.isAfter(currentTxnMinTime) || time.equals(currentTxnMinTime));
	}

	/**
	 * @param offset an offset from the current transaction consensus time
	 * @return true if the current transaction consensus time minus offset should be allowed as a
	 *         record consensus time in a "preceding" record.
	 */
	public boolean isAllowablePrecedingOffset(final long offset) {
		return isAllowablePrecedingTime(currentTxnTime.minusNanos(offset));
	}

	private Instant nextTime(final boolean canTriggerTxn, final long maxPreceding, final long maxFollowing) {

		if (!hasMoreTime(canTriggerTxn, maxPreceding, maxFollowing)) {
			log.error("Cannot get more transaction times! {}", this);
			throw new IllegalStateException("Cannot get more transaction times!");
		}

		firstUsed = true;

		currentTxnMinTime = currentTxnTime.plusNanos(followingRecordsCount + 1);

		currentTxnTime = currentTxnMinTime.plusNanos(maxPreceding);

		currentTxnMaxTime = currentTxnTime.plusNanos(maxFollowing);

		followingRecordsCount = maxFollowing;

		return currentTxnTime;
	}

	private boolean hasMoreTime(boolean canTriggerTxn, final long maxPreceding, final long maxFollowing) {
		var next = currentTxnTime.plusNanos(followingRecordsCount);

		if (next.isAfter(currentTxnMaxTime)) {
			log.warn("Used more record slots than allowed per transaction! {}", this);
		}

		if (next.isAfter(maxConsensusTime)) {
			log.error("Exceeded the max nanos per handleTransaction() call! {}", this);
			throw new IllegalStateException("Exceeded the max nanos per handleTransaction() call!");
		}

		return !next
				.plusNanos((1L + maxPreceding + maxFollowing) * (canTriggerTxn ? 2L : 1L))
				.isAfter(maxConsensusTime);
	}

	@Override
	public String toString() {
		return "ConsensusTimeTracker{" +
				"nanosPerRound=" + nanosPerRound +
				", roundStartConsensusTime=" + roundStartConsensusTime +
				", currentTxnMinTime=" + currentTxnMinTime +
				", currentTxnTime=" + currentTxnTime +
				", currentTxnMaxTime=" + currentTxnMaxTime +
				", maxConsensusTime=" + maxConsensusTime +
				", followingRecordsCount=" + followingRecordsCount +
				", firstUsed=" + firstUsed +
				'}';
	}

	@VisibleForTesting
	Instant getRoundStartConsensusTime() {
		return roundStartConsensusTime;
	}

	@VisibleForTesting
	Instant getCurrentTxnMinTime() {
		return currentTxnMinTime;
	}

	@VisibleForTesting
	Instant getCurrentTxnTime() {
		return currentTxnTime;
	}

	@VisibleForTesting
	Instant getCurrentTxnMaxTime() {
		return currentTxnMaxTime;
	}

	@VisibleForTesting
	Instant getMaxConsensusTime() {
		return maxConsensusTime;
	}

	@VisibleForTesting
	long getFollowingRecordsCount() {
		return followingRecordsCount;
	}
}
