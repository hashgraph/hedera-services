package com.hedera.services.legacy.core.jproto;

import com.hedera.services.state.submerkle.TxnId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_NEW_TOTAL_SUPPLY;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH_VERSION;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_TOPIC_SEQ_NO;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.REVERTED_SUCCESS_LITERAL;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TxnReceiptBuilderTest {
	private TxnReceipt.Builder subject;

	@BeforeEach
	void setUp() {
		subject = TxnReceipt.newBuilder();
	}

	@Test
	void doesntOverrideStatusForUnsuccessful() {
		final var failureStatus = "INVALID_ACCOUNT_ID";

		subject.setStatus(failureStatus);

		subject.revert();

		assertSame(failureStatus, subject.getStatus());
	}

	@Test
	void revertsSideEffectsForSuccessAsExpected() {
		subject.setStatus("SUCCESS");
		subject.setAccountId(MISSING_ENTITY_ID);
		subject.setContractId(MISSING_ENTITY_ID);
		subject.setFileId(MISSING_ENTITY_ID);
		subject.setTokenId(MISSING_ENTITY_ID);
		subject.setTopicId(MISSING_ENTITY_ID);
		subject.setScheduleId(MISSING_ENTITY_ID);
		subject.setScheduledTxnId(new TxnId());
		subject.setNewTotalSupply(123);
		subject.setSerialNumbers(new long[] { 1, 2, 3 });
		subject.setRunningHashVersion(1);
		subject.setTopicRunningHash("ABC".getBytes());
		subject.setTopicSequenceNumber(321);

		subject.revert();

		assertEquals(REVERTED_SUCCESS_LITERAL, subject.getStatus());
		assertNull(subject.getAccountId());
		assertNull(subject.getContractId());
		assertNull(subject.getFileId());
		assertNull(subject.getTokenId());
		assertNull(subject.getTopicId());
		assertNull(subject.getScheduleId());
		assertNull(subject.getScheduledTxnId());
		assertNull(subject.getSerialNumbers());
		assertNull(subject.getTopicRunningHash());
		assertEquals(MISSING_TOPIC_SEQ_NO, subject.getTopicSequenceNumber());
		assertEquals(MISSING_NEW_TOTAL_SUPPLY, subject.getNewTotalSupply());
		assertEquals(MISSING_RUNNING_HASH_VERSION, subject.getRunningHashVersion());
	}
}