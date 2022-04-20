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

	public static final long MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND = 10;
	public static final long MAX_PRECEDING_RECORDS_REMAINING_TXN = 3;
	public static final long MAX_FOLLOWING_RECORDS = 10;

	private Instant roundStartConsensusTime;
	private Instant currentTxnMinTime;
	private Instant currentTxnTime;
	private Instant currentTxnMaxTime;
	private Instant maxConsensusTime;

	private long followingRecordsCount;

	@Inject
	public ConsensusTimeTracker() {
	}

	public void setActualFollowingRecordsCount(final long count) {
		followingRecordsCount = count;
	}

	public void reset(final Instant consensusTime) {
		roundStartConsensusTime = consensusTime;
		currentTxnMinTime = null;
		currentTxnTime = null;
		currentTxnMaxTime = null;
		followingRecordsCount = 0;
		maxConsensusTime = consensusTime.plusNanos(TransactionHandler.MIN_TRANS_TIMESTAMP_INCR_NANOS - 1);
	}

	public Instant firstTransactionTime() {
		currentTxnMinTime = roundStartConsensusTime;
		currentTxnTime = roundStartConsensusTime.plusNanos(MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND);
		currentTxnMaxTime = currentTxnTime.plusNanos(MAX_FOLLOWING_RECORDS);
		followingRecordsCount = 0;

		return currentTxnTime;
	}

	public Instant nextTransactionTime(boolean canTriggerTxn) {
		return nextTime(canTriggerTxn, MAX_PRECEDING_RECORDS_REMAINING_TXN, MAX_FOLLOWING_RECORDS);
	}

	public boolean hasMoreTransactionTime(boolean canTriggerTxn) {
		return hasMoreTime(canTriggerTxn, MAX_PRECEDING_RECORDS_REMAINING_TXN, MAX_FOLLOWING_RECORDS);
	}

	public Instant nextStandaloneRecordTime() {
		return nextTime(false, 0, 0);
	}

	public boolean hasMoreStandaloneRecordTime() {
		return hasMoreTime(false, 0, 0);
	}

	public boolean isAllowableFollowingTime(final Instant time) {
		return time.isAfter(currentTxnTime) && (!time.isAfter(currentTxnMaxTime));
	}

	public boolean isAllowableFollowingOffset(final long offset) {
		return isAllowableFollowingTime(currentTxnTime.plusNanos(offset));
	}

	public boolean isAllowablePrecedingTime(final Instant time) {
		return (!time.isAfter(currentTxnTime)) && time.isAfter(currentTxnMinTime);
	}

	public boolean isAllowablePrecedingOffset(final long offset) {
		return isAllowablePrecedingTime(currentTxnTime.minusNanos(offset));
	}

	private Instant nextTime(final boolean canTriggerTxn, final long maxPreceding, final long maxFollowing) {

		if (!hasMoreTime(canTriggerTxn, maxPreceding, maxFollowing)) {
			log.error("Cannot get more transaction times! {}", this);
			throw new IllegalStateException("Cannot get more transaction times!");
		}

		currentTxnMinTime = currentTxnTime.plusNanos(followingRecordsCount + 1);

		currentTxnTime = currentTxnMinTime.plusNanos(maxPreceding);

		currentTxnMaxTime = currentTxnTime.plusNanos(maxFollowing);

		followingRecordsCount = 0;

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
				.plusNanos((1L + maxPreceding + maxFollowing) * (canTriggerTxn ? 1L : 2L))
				.isAfter(maxConsensusTime);
	}

	@Override
	public String toString() {
		return "ConsensusTimeTracker{" +
				"roundStartConsensusTime=" + roundStartConsensusTime +
				", currentTxnMinTime=" + currentTxnMinTime +
				", currentTxnTime=" + currentTxnTime +
				", currentTxnMaxTime=" + currentTxnMaxTime +
				", maxConsensusTime=" + maxConsensusTime +
				'}';
	}
}
