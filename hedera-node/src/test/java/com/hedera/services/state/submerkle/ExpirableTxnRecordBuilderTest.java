package com.hedera.services.state.submerkle;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpirableTxnRecordBuilderTest {
	private static final long parentConsSec = 1_234_567L;
	private static final int parentConsNanos = 890;
	private static final long packedParentConsTime = packedTime(parentConsSec, parentConsNanos);
	private static final Instant parentConsTime = Instant.ofEpochSecond(parentConsSec, parentConsNanos);

	@Mock
	private TxnReceipt.Builder receiptBuilder;

	private ExpirableTxnRecord.Builder subject;

	@BeforeEach
	void setUp() {
		subject = ExpirableTxnRecord.newBuilder();
	}

	@Test
	void builderPropagatesChildTxnMeta() {
		subject.setNumChildRecords((short) 12);
		subject.setParentConsensusTime(parentConsTime);

		final var result = subject.build();
		assertEquals(12, result.getNumChildRecords());
		assertEquals(packedParentConsTime, result.getPackedParentConsensusTime());
	}

	@Test
	void parentConsensusTimeMappedToAndFromGrpc() {
		final var grpcRecord = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder().setAccountID(IdUtils.asAccount("0.0.3")))
				.setTransactionID(TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
				.setConsensusTimestamp(MiscUtils.asTimestamp(parentConsTime.plusNanos(1)))
				.setParentConsensusTimestamp(MiscUtils.asTimestamp(parentConsTime))
				.build();

		final var subject = ExpirableTxnRecordTestHelper.fromGprc(grpcRecord);

		assertEquals(grpcRecord, subject.asGrpc());
	}

	@Test
	void usesReceiptBuilderIfPresent() {
		final var status = "INVALID_ACCOUNT_ID";
		final var statusReceipt = TxnReceipt.newBuilder().setStatus(status);
		subject.setReceiptBuilder(statusReceipt);
		final var record = subject.build();
		assertEquals(status, record.getReceipt().getStatus());
	}

	@Test
	void subtractingOffNoHbarAdjustsIsNoop() {
		final var that = ExpirableTxnRecord.newBuilder();

		final var someAdjusts = new CurrencyAdjustments(new long[] { +1, -1
		}, List.of(new EntityId(0, 0, 1), new EntityId(0, 0, 2)));
		subject.setTransferList(someAdjusts);

		subject.excludeHbarChangesFrom(that);

		assertSame(someAdjusts, subject.getTransferList());
	}

	@Test
	void canSubtractOffExcludedHbarAdjustmentsWithSameStop() {
		final var inThisButNotThat = new EntityId(0, 0, 2);
		final var firstInBoth = new EntityId(0, 0, 3);
		final var secondInBoth = new EntityId(0, 0, 4);
		final var inThatButNotThis = new EntityId(0, 0, 5);
		final var thirdInBoth = new EntityId(0, 0, 6);

		final var thisAdjusts = new CurrencyAdjustments(new long[] {
				-10, +6, +3, +1
		}, List.of(inThisButNotThat, firstInBoth, secondInBoth, thirdInBoth));
		final var thatAdjusts = new CurrencyAdjustments(new long[] {
				-2, -4, +5, +1
		}, List.of(firstInBoth, secondInBoth, inThatButNotThis, thirdInBoth));

		final var that = ExpirableTxnRecord.newBuilder();
		that.setTransferList(thatAdjusts);

		subject.setTransferList(thisAdjusts);
		subject.excludeHbarChangesFrom(that);

		final var expectedChanges = new long[] { -10, +8, +7, -5 };
		final var expectedAccounts = List.of(
				inThisButNotThat, firstInBoth, secondInBoth, inThatButNotThis);
		assertArrayEquals(expectedChanges, subject.getTransferList().hbars);
		assertEquals(expectedAccounts, subject.getTransferList().accountIds);
	}

	@Test
	void canSubtractOffExcludedHbarAdjustmentsWithThisEarlyStop() {
		final var firstInBoth = new EntityId(0, 0, 3);
		final var secondInBoth = new EntityId(0, 0, 4);
		final var inThatButNotThis = new EntityId(0, 0, 5);

		final var thisAdjusts = new CurrencyAdjustments(new long[] {
				+6, +3
		}, List.of(firstInBoth, secondInBoth));
		final var thatAdjusts = new CurrencyAdjustments(new long[] {
				-2, -4, +5
		}, List.of(firstInBoth, secondInBoth, inThatButNotThis));

		final var that = ExpirableTxnRecord.newBuilder();
		that.setTransferList(thatAdjusts);

		subject.setTransferList(thisAdjusts);
		subject.excludeHbarChangesFrom(that);

		final var expectedChanges = new long[] { +8, +7, -5 };
		final var expectedAccounts = List.of(
				firstInBoth, secondInBoth, inThatButNotThis);
		assertArrayEquals(expectedChanges, subject.getTransferList().hbars);
		assertEquals(expectedAccounts, subject.getTransferList().accountIds);
	}

	@Test
	void canSubtractOffExcludedHbarAdjustmentsWithThatEarlyStop() {
		final var firstInThisButNotThat = new EntityId(0, 0, 2);
		final var firstInBoth = new EntityId(0, 0, 3);
		final var secondInBoth = new EntityId(0, 0, 4);
		final var secondInThisButNotThat = new EntityId(0, 0, 6);

		final var thisAdjusts = new CurrencyAdjustments(new long[] {
				+10, +6, +3, -19
		}, List.of(firstInThisButNotThat, firstInBoth, secondInBoth, secondInThisButNotThat));
		final var thatAdjusts = new CurrencyAdjustments(new long[] {
				+2, +4
		}, List.of(firstInBoth, secondInBoth));

		final var that = ExpirableTxnRecord.newBuilder();
		that.setTransferList(thatAdjusts);

		subject.setTransferList(thisAdjusts);
		subject.excludeHbarChangesFrom(that);

		final var expectedChanges = new long[] { +10, +4, -1, -19 };
		final var expectedAccounts = List.of(
				firstInThisButNotThat, firstInBoth, secondInBoth, secondInThisButNotThat);
		assertArrayEquals(expectedChanges, subject.getTransferList().hbars);
		assertEquals(expectedAccounts, subject.getTransferList().accountIds);
	}

	@Test
	void revertClearsAllSideEffects() {
		subject.setTokens(List.of(MISSING_ENTITY_ID));
		subject.setTransferList(new CurrencyAdjustments(new long[] { 1 }, List.of(MISSING_ENTITY_ID)));
		subject.setReceiptBuilder(receiptBuilder);
		subject.setTokenAdjustments(List.of(new CurrencyAdjustments(new long[] { 1 }, List.of(MISSING_ENTITY_ID))));
		subject.setContractCallResult(new SolidityFnResult());
		subject.setNftTokenAdjustments(List.of(new NftAdjustments()));
		subject.setContractCreateResult(new SolidityFnResult());
		subject.setNewTokenAssociations(List.of(new FcTokenAssociation(1, 2)));
		subject.setAssessedCustomFees(List.of(new FcAssessedCustomFee(MISSING_ENTITY_ID, 1, new long[] { 1L })));
		subject.setAlias(ByteString.copyFromUtf8("aaa"));

		subject.revert();

		verify(receiptBuilder).revert();

		assertNull(subject.getTokens());
		assertNull(subject.getScheduleRef());
		assertNull(subject.getTransferList());
		assertNull(subject.getTokenAdjustments());
		assertNull(subject.getContractCallResult());
		assertNull(subject.getNftTokenAdjustments());
		assertNull(subject.getContractCreateResult());
		assertNull(subject.getAssessedCustomFees());
		assertTrue(subject.getNewTokenAssociations().isEmpty());
		assertTrue(subject.getAlias().isEmpty());
	}

	@Test
	void revertOnlyPossibleWithReceiptBuilder() {
		subject.setReceipt(new TxnReceipt());

		assertThrows(IllegalStateException.class, subject::revert);
	}
}
