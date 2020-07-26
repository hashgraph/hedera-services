package com.hedera.services.records;

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
	public void returnsExpectedLegacyQueryableRecord() {
		// setup:
		var classifiable = mock(ExpirableTxnRecord.class);
		var unclassifiable = mock(ExpirableTxnRecord.class);

		// expect:
		assertNull(subject.legacyQueryableRecord());
		// and given:
		subject.unclassifiableRecords = List.of(unclassifiable);
		// expect:
		assertNull(subject.legacyQueryableRecord());
		// and given:
		subject.classifiableRecords = List.of(classifiable);
		Assertions.assertSame(classifiable, subject.legacyQueryableRecord());
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
	public void forgetsAsExpected() {
		// given:
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