package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class TxnIdRecentHistoryTest {
	Instant now = Instant.now();

	TxnIdRecentHistory subject;

	@BeforeEach
	public void setup() {
		subject = new TxnIdRecentHistory();
	}

	@Test
	public void getsMemory() {
		// given:
		subject.classifiableRecords = List.of(mock(ExpirableTxnRecord.class));
		// expect:
		assertFalse(subject.isForgotten());

		// and given:
		subject.classifiableRecords = null;
		subject.unclassifiableRecords = List.of(mock(ExpirableTxnRecord.class));
		// expect:
		assertFalse(subject.isForgotten());

		// and given:
		subject.unclassifiableRecords = null;
		// expect:
		assertTrue(subject.isForgotten());
	}

	@Test
	public void classifiesAsExpected() {
		// given:
		subject.observe(
				recordOf(0, 0, INVALID_NODE_ACCOUNT),
				INVALID_NODE_ACCOUNT);
		// expect:
		assertEquals(BELIEVED_UNIQUE, subject.currentDuplicityFor(1));

		// and given:
		subject.observe(
				recordOf(1, 1, SUCCESS),
				SUCCESS);
		// expect:
		assertEquals(NODE_DUPLICATE, subject.currentDuplicityFor(1));
		assertEquals(DUPLICATE, subject.currentDuplicityFor(2));
	}

	@Test
	public void restoresFromStagedAsExpected() {
		// given:
		subject.stage(recordOf(2, 7, INVALID_NODE_ACCOUNT));
		subject.stage(recordOf(1, 1, SUCCESS));
		subject.stage(recordOf(1, 0, INVALID_PAYER_SIGNATURE));
		subject.stage(recordOf(2, 3, DUPLICATE_TRANSACTION));
		subject.stage(recordOf(1, 2, DUPLICATE_TRANSACTION));
		subject.stage(recordOf(3, 5, DUPLICATE_TRANSACTION));
		subject.stage(recordOf(1, 6, INVALID_PAYER_SIGNATURE));
		subject.stage(recordOf(2, 4, DUPLICATE_TRANSACTION));

		// when:
		subject.observeStaged();

		// then:
		assertEquals(
				List.of(
						memoIdentifying(1, 1, SUCCESS),
						memoIdentifying(2, 3, DUPLICATE_TRANSACTION),
						memoIdentifying(3, 5, DUPLICATE_TRANSACTION),
						memoIdentifying(1, 2, DUPLICATE_TRANSACTION),
						memoIdentifying(2, 4, DUPLICATE_TRANSACTION)
				), subject.classifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
		// and:
		assertEquals(
				List.of(
						memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
						memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
						memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)
				), subject.unclassifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
		// and:
		assertNull(subject.memory);
	}

	@Test
	public void prioritizesClassifiableRecords() {
		givenSomeWellKnownHistory();

		// when:
		var priority = subject.priorityRecord();

		// then:
		assertEquals(
				memoIdentifying(1, 1, SUCCESS),
				priority.getMemo());
	}

	@Test
	public void returnsEmptyIfForgotten() {
		// expect:
		assertNull(subject.priorityRecord());
	}

	@Test
	public void returnsUnclassifiableIfOnlyAvailable() {
		// given:
		subject.observe(
				recordOf(1, 0, INVALID_PAYER_SIGNATURE),
				INVALID_PAYER_SIGNATURE);

		// when:
		var priority = subject.priorityRecord();

		// then:
		assertEquals(
				memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
				priority.getMemo());
	}

	@Test
	public void forgetsAsExpected() {
		givenSomeWellKnownHistory();

		// when:
		subject.forgetExpiredAt(expiryAtOffset(4));

		// then:
		assertEquals(
				List.of(
						memoIdentifying(3, 5, DUPLICATE_TRANSACTION)
				), subject.classifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
		// and:
		assertEquals(
				List.of(
						memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
						memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)
				), subject.unclassifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
	}

	@Test
	public void omitsPriorityWhenUnclassifiable() {
		// given:
		subject.observe(
				recordOf(1, 0, INVALID_PAYER_SIGNATURE),
				INVALID_PAYER_SIGNATURE);
		subject.observe(
				recordOf(2, 1, INVALID_NODE_ACCOUNT),
				INVALID_NODE_ACCOUNT);

		// when:
		var duplicates = subject.duplicateRecords();

		// then:
		assertEquals(
				List.of(
					memoIdentifying(2, 1, INVALID_NODE_ACCOUNT)
				), duplicates.stream().map(sr -> sr.getMemo()).collect(toList()));
	}

	@Test
	public void returnsOrderedDuplicates() {
		givenSomeWellKnownHistory();

		// when:
		var records = subject.duplicateRecords();

		// then:
		assertEquals(
				List.of(
						memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
						memoIdentifying(1, 2, DUPLICATE_TRANSACTION),
						memoIdentifying(2, 3, DUPLICATE_TRANSACTION),
						memoIdentifying(2, 4, DUPLICATE_TRANSACTION),
						memoIdentifying(3, 5, DUPLICATE_TRANSACTION),
						memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
						memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)
				), records.stream().map(sr -> sr.getMemo()).collect(toList()));
	}

	private void givenSomeWellKnownHistory() {
		subject.observe(
				recordOf(1, 0, INVALID_PAYER_SIGNATURE),
				INVALID_PAYER_SIGNATURE);
		subject.observe(
				recordOf(1, 1, SUCCESS),
				SUCCESS);
		subject.observe(
				recordOf(1, 2, DUPLICATE_TRANSACTION),
				DUPLICATE_TRANSACTION);
		subject.observe(
				recordOf(2, 3, DUPLICATE_TRANSACTION),
				DUPLICATE_TRANSACTION);
		subject.observe(
				recordOf(2, 4, DUPLICATE_TRANSACTION),
				DUPLICATE_TRANSACTION);
		subject.observe(
				recordOf(3, 5, DUPLICATE_TRANSACTION),
				DUPLICATE_TRANSACTION);
		subject.observe(
				recordOf(1, 6, INVALID_PAYER_SIGNATURE),
				INVALID_PAYER_SIGNATURE);
		subject.observe(
				recordOf(2, 7, INVALID_NODE_ACCOUNT),
				INVALID_NODE_ACCOUNT);
	}

	private ExpirableTxnRecord recordOf(
			long submittingMember,
			long consensusOffsetSecs,
			ResponseCodeEnum status) {
		var payerRecord = TransactionRecord.newBuilder()
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(consensusOffsetSecs))
				.setMemo(memoIdentifying(submittingMember, consensusOffsetSecs, status))
				.setReceipt(TransactionReceipt.newBuilder().setStatus(status))
				.build();
		var expirableRecord = ExpirableTxnRecord.fromGprc(payerRecord);
		expirableRecord.setExpiry(expiryAtOffset(consensusOffsetSecs));
		expirableRecord.setSubmittingMember(submittingMember);
		return expirableRecord;
	}

	private long expiryAtOffset(long l) {
		return now.getEpochSecond() + 1 + l;
	}

	private String memoIdentifying(long submittingMember, long consensusOffsetSecs, ResponseCodeEnum status) {
		return String.format("%d submitted @ %d past -> %s", submittingMember, consensusOffsetSecs, status);
	}
}
