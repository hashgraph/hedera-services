package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static com.hedera.services.records.ConsensusTimeTracker.MAX_FOLLOWING_RECORDS;
import static com.hedera.services.records.ConsensusTimeTracker.MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND;
import static com.hedera.services.records.ConsensusTimeTracker.MAX_PRECEDING_RECORDS_REMAINING_TXN;
import static com.hedera.services.records.ConsensusTimeTracker.DEFAULT_NANOS_PER_ROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({ LogCaptureExtension.class })
class ConsensusTimeTrackerTest {
	private static final Instant consensusTime = Instant.ofEpochSecond(2, 20);

	@LoggingTarget
	private LogCaptor logCaptor;

	@LoggingSubject
	private ConsensusTimeTracker subject;


	@BeforeEach
	void setUp() {
		subject = new ConsensusTimeTracker();
	}

	@Test
	void happyPathWorksAsExpected() {

		subject.reset(consensusTime);

		ConsensusTimeTracker previous = null;

		assertTrue(subject.hasMoreTransactionTime(true));
		assertFalse(subject.isFirstUsed());

		Instant txnTime = subject.firstTransactionTime();

		assertTrue(subject.hasMoreStandaloneRecordTime());
		assertTrue(subject.hasMoreTransactionTime(true));
		assertTrue(subject.hasMoreTransactionTime(false));

		long maxPreceding = MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND;
		long maxFollowing = MAX_FOLLOWING_RECORDS;
		int count = 0;
		int standaloneCount = 0;
		int moreTxnCount = 0;
		long actualFollowing = 2;
		boolean isStandalone = false;
		boolean nextCanTriggerTxn = true;

		while (true) {
			++count;

			checkBounds(previous, isStandalone);

			assertEquals(txnTime, subject.getCurrentTxnTime());

			assertTrue(subject.isFirstUsed());

			if (count > 1) {
				assertTrue(subject.getRoundStartConsensusTime().isBefore(subject.getCurrentTxnMinTime()));
			} else {
				assertEquals(subject.getRoundStartConsensusTime(), subject.getCurrentTxnMinTime());
			}

			long availableCount = 0;

			for (int x = -5; x <= (maxPreceding + 5); ++x) {
				var time = txnTime.minusNanos(x);
				var msg = "x = " + x + " isStandalone = " + isStandalone;

				assertEquals(subject.isAllowablePrecedingTime(time),
						x > 0 && x <= maxPreceding, msg);
				assertEquals(subject.isAllowablePrecedingOffset(x),
						x > 0 && x <= maxPreceding, msg);

				if (subject.isAllowablePrecedingTime(time)) {
					++availableCount;
					if (x == maxPreceding) {
						assertEquals(time, subject.getCurrentTxnMinTime(), msg);
					} else {
						assertTrue(time.isAfter(subject.getCurrentTxnMinTime()), msg);
					}
					assertTrue(time.isBefore(subject.getCurrentTxnTime()), msg);
					assertTrue(time.isBefore(subject.getCurrentTxnMaxTime()), msg);
				}
			}

			for (int x = -5; x <= (maxFollowing + 5); ++x) {
				var time = txnTime.plusNanos(x);
				var msg = "x = " + x + " isStandalone = " + isStandalone;

				assertEquals(subject.isAllowableFollowingTime(time),
						x > 0 && x <= maxFollowing, msg);
				assertEquals(subject.isAllowableFollowingOffset(x),
						x > 0 && x <= maxFollowing, msg);

				if (subject.isAllowableFollowingTime(time)) {
					++availableCount;
					if (x == maxFollowing) {
						assertEquals(time, subject.getCurrentTxnMaxTime(), msg);
					} else {
						assertTrue(time.isBefore(subject.getCurrentTxnMaxTime()), msg);
					}
					assertTrue(time.isAfter(subject.getCurrentTxnTime()), msg);
					assertTrue(time.isAfter(subject.getCurrentTxnMinTime()), msg);
				}
			}

			assertEquals(availableCount, maxFollowing + maxPreceding);

			var hadMore = subject.hasMoreTransactionTime(nextCanTriggerTxn);

			subject.setActualFollowingRecordsCount(DEFAULT_NANOS_PER_ROUND);
			assertThrows(IllegalStateException.class, () -> subject.hasMoreTransactionTime(false));
			assertThrows(IllegalStateException.class, () -> subject.hasMoreStandaloneRecordTime());

			subject.setActualFollowingRecordsCount(actualFollowing);

			if (hadMore) {
				assertTrue(subject.hasMoreTransactionTime(nextCanTriggerTxn));
			}

			if (nextCanTriggerTxn) {
				if (!subject.hasMoreTransactionTime(true)) {
					nextCanTriggerTxn = false;
				}
				assertTrue(subject.hasMoreTransactionTime(false));
			} else {
				assertFalse(subject.hasMoreTransactionTime(false));
			}

			if (subject.hasMoreTransactionTime(nextCanTriggerTxn)) {

				if (nextCanTriggerTxn) {
					assertTrue(subject.hasMoreTransactionTime(false));
				}

				assertTrue(subject.hasMoreStandaloneRecordTime());

				previous = new ConsensusTimeTracker(subject);

				txnTime = subject.nextTransactionTime(nextCanTriggerTxn);

				isStandalone = false;
				++moreTxnCount;

				++actualFollowing;
				if (actualFollowing > MAX_FOLLOWING_RECORDS) {
					actualFollowing = 0;
				}
				maxPreceding = MAX_PRECEDING_RECORDS_REMAINING_TXN;
				maxFollowing = MAX_FOLLOWING_RECORDS;

			} else if (subject.hasMoreStandaloneRecordTime()) {

				++standaloneCount;

				isStandalone = true;

				previous = new ConsensusTimeTracker(subject);

				txnTime = subject.nextStandaloneRecordTime();

				maxPreceding = 0;
				maxFollowing = 0;
				actualFollowing = 0;

			} else {

				assertFalse(subject.hasMoreTransactionTime(true));
				assertFalse(subject.hasMoreTransactionTime(false));
				assertFalse(subject.hasMoreStandaloneRecordTime());

				assertThrows(IllegalStateException.class, () -> subject.nextStandaloneRecordTime());
				assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(true));
				assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(false));

				break;
			}

		}

		assertFalse(nextCanTriggerTxn);
		assertTrue(moreTxnCount > 50);
		assertTrue(standaloneCount > 3);

	}

	@Test
	void transactionAsFirstWorksAsExpected() {
		subject.reset(consensusTime);
		assertFalse(subject.isFirstUsed());

		subject.nextTransactionTime(false);
		assertTrue(subject.isFirstUsed());
		assertTrue(subject.isAllowableFollowingOffset(MAX_FOLLOWING_RECORDS));
		assertTrue(subject.isAllowablePrecedingOffset(MAX_PRECEDING_RECORDS_REMAINING_TXN));

		checkBounds(null, false);

		assertThrows(IllegalStateException.class, () -> subject.firstTransactionTime());
	}

	@Test
	void standaloneAsFirstWorksAsExpected() {
		subject.reset(consensusTime);
		assertFalse(subject.isFirstUsed());

		subject.nextStandaloneRecordTime();
		assertTrue(subject.isFirstUsed());
		assertFalse(subject.isAllowableFollowingOffset(1));
		assertFalse(subject.isAllowablePrecedingOffset(1));

		checkBounds(null, true);

		assertThrows(IllegalStateException.class, () -> subject.firstTransactionTime());
	}

	@Test
	void firstTransactionTimeWorksAsExpected() {
		subject.reset(consensusTime);
		assertFalse(subject.isFirstUsed());

		subject.firstTransactionTime();
		assertTrue(subject.isFirstUsed());
		assertTrue(subject.isAllowableFollowingOffset(MAX_FOLLOWING_RECORDS));
		assertTrue(subject.isAllowablePrecedingOffset(MAX_PRECEDING_RECORDS_FIRST_TXN_IN_ROUND));

		checkBounds(null, false);

		assertThrows(IllegalStateException.class, () -> subject.firstTransactionTime());
	}

	@Test
	void warningOnUsageOverflowLessThanMax() {
		subject.reset(consensusTime);
		subject.firstTransactionTime();

		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS);
		assertTrue(subject.hasMoreTransactionTime(false));
		assertTrue(subject.hasMoreTransactionTime(true));
		assertTrue(subject.hasMoreStandaloneRecordTime());
		subject.nextTransactionTime(false);
		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS);
		subject.nextTransactionTime(true);
		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS);
		subject.nextStandaloneRecordTime();

		assertEquals(0, logCaptor.warnLogs().stream().filter(
				s -> s.contains("Used more record slots than allowed per transaction")).count());

		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS + 1);
		assertTrue(subject.hasMoreTransactionTime(false));
		assertTrue(subject.hasMoreTransactionTime(true));
		assertTrue(subject.hasMoreStandaloneRecordTime());
		subject.nextTransactionTime(false);
		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS + 1);
		subject.nextTransactionTime(true);
		subject.setActualFollowingRecordsCount(MAX_FOLLOWING_RECORDS + 1);
		subject.nextStandaloneRecordTime();

		assertEquals(6, logCaptor.warnLogs().stream().filter(
				s -> s.contains("Used more record slots than allowed per transaction")).count());
	}

	@Test
	void errorOnUsageOverflowGreaterThanMax() {
		subject.reset(consensusTime);
		subject.firstTransactionTime();

		subject.setActualFollowingRecordsCount(DEFAULT_NANOS_PER_ROUND - MAX_FOLLOWING_RECORDS - 1);
		assertFalse(subject.hasMoreTransactionTime(false));
		assertFalse(subject.hasMoreTransactionTime(true));
		assertFalse(subject.hasMoreStandaloneRecordTime());

		// these throw because there should not be any more times
		assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(false));
		assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(true));
		assertThrows(IllegalStateException.class, () -> subject.nextStandaloneRecordTime());

		subject.setActualFollowingRecordsCount(DEFAULT_NANOS_PER_ROUND - MAX_FOLLOWING_RECORDS);

		assertThrows(IllegalStateException.class, () -> subject.hasMoreTransactionTime(false));
		assertThrows(IllegalStateException.class, () -> subject.hasMoreTransactionTime(true));
		assertThrows(IllegalStateException.class, () -> subject.hasMoreStandaloneRecordTime());
		assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(false));
		assertThrows(IllegalStateException.class, () -> subject.nextTransactionTime(true));
		assertThrows(IllegalStateException.class, () -> subject.nextStandaloneRecordTime());

	}

	@Test
	void errorOnNanosPerRoundTooSmall() {
		assertThrows(IllegalArgumentException.class, () -> new ConsensusTimeTracker(1));
	}

	@Test
	void errorOnSetActualFollowingRecordsCountLessThanZero() {
		assertThrows(IllegalArgumentException.class,
				() -> subject.setActualFollowingRecordsCount(-1));
	}

	private void checkBounds(ConsensusTimeTracker previous, boolean isStandalone) {
		var minTime = consensusTime;
		var maxTime = consensusTime.plusNanos(DEFAULT_NANOS_PER_ROUND);

		assertTrue(maxTime.isAfter(subject.getRoundStartConsensusTime()));
		assertTrue(maxTime.isAfter(subject.getCurrentTxnMinTime()));
		assertTrue(maxTime.isAfter(subject.getCurrentTxnTime()));
		assertTrue(maxTime.isAfter(subject.getCurrentTxnMaxTime()));
		assertTrue(maxTime.isAfter(subject.getMaxConsensusTime()));

		assertTrue(minTime.compareTo(subject.getRoundStartConsensusTime()) <= 0);
		assertTrue(minTime.compareTo(subject.getCurrentTxnMinTime()) <= 0);
		assertTrue(minTime.compareTo(subject.getCurrentTxnTime()) <= 0);
		assertTrue(minTime.compareTo(subject.getCurrentTxnMaxTime()) <= 0);
		assertTrue(minTime.compareTo(subject.getMaxConsensusTime()) <= 0);


		assertTrue(subject.getRoundStartConsensusTime().compareTo(subject.getCurrentTxnMinTime()) <= 0);
		assertTrue(subject.getRoundStartConsensusTime().isBefore(subject.getCurrentTxnTime()));
		assertTrue(subject.getRoundStartConsensusTime().isBefore(subject.getCurrentTxnMaxTime()));
		assertTrue(subject.getRoundStartConsensusTime().isBefore(subject.getMaxConsensusTime()));

		if (isStandalone) {
			assertEquals(subject.getCurrentTxnMinTime(), subject.getCurrentTxnTime());
			assertEquals(subject.getCurrentTxnMinTime(), subject.getCurrentTxnMaxTime());
			assertTrue(subject.getCurrentTxnMinTime().compareTo(subject.getMaxConsensusTime()) <= 0);
		} else {
			assertTrue(subject.getCurrentTxnMinTime().isBefore(subject.getCurrentTxnTime()));
			assertTrue(subject.getCurrentTxnMinTime().isBefore(subject.getCurrentTxnMaxTime()));
			assertTrue(subject.getCurrentTxnMinTime().isBefore(subject.getMaxConsensusTime()));
		}

		if (isStandalone) {
			assertEquals(subject.getCurrentTxnMaxTime(), subject.getCurrentTxnTime());
			assertTrue(subject.getCurrentTxnMaxTime().compareTo(subject.getMaxConsensusTime()) <= 0);
		} else {
			assertTrue(subject.getCurrentTxnMaxTime().isAfter(subject.getCurrentTxnTime()));
			assertTrue(subject.getCurrentTxnMaxTime().compareTo(subject.getMaxConsensusTime()) <= 0);
		}

		if (previous != null) {
			var prev = previous;

			assertEquals(prev.getRoundStartConsensusTime(), subject.getRoundStartConsensusTime());
			assertEquals(prev.getMaxConsensusTime(), subject.getMaxConsensusTime());

			assertTrue(subject.getCurrentTxnMinTime().isAfter(prev.getCurrentTxnTime()
					.plusNanos(prev.getFollowingRecordsCount())));
		}

	};
}
