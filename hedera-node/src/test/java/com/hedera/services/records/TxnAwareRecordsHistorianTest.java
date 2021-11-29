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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;


@ExtendWith(MockitoExtension.class)
class TxnAwareRecordsHistorianTest {
	final private long submittingMember = 1L;
	final private AccountID a = asAccount("0.0.1111");
	final private EntityId aEntity = EntityId.fromGrpcAccountId(a);
	final private TransactionID txnIdA = TransactionID.newBuilder().setAccountID(a).build();
	final private AccountID b = asAccount("0.0.2222");
	final private AccountID c = asAccount("0.0.3333");
	final private AccountID effPayer = asAccount("0.0.5555");
	final private Instant now = Instant.now();
	final private long nows = now.getEpochSecond();
	final int payerRecordTtl = 180;
	final long payerExpiry = now.getEpochSecond() + payerRecordTtl;
	final private AccountID d = asAccount("0.0.4444");
	final private AccountID funding = asAccount("0.0.98");
	final private TransferList initialTransfers = withAdjustments(
			a, -1_000L, b, 500L, c, 501L, d, -1L);
	final private ExpirableTxnRecord.Builder finalRecord = ExpirableTxnRecord.newBuilder()
			.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder().setAccountID(a).build()))
			.setTransferList(CurrencyAdjustments.fromGrpc(initialTransfers))
			.setMemo("This is different!")
			.setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build());
	final private ExpirableTxnRecord.Builder jFinalRecord = finalRecord;
	final private ExpirableTxnRecord payerRecord = finalRecord.build();
	{
		payerRecord.setExpiry(payerExpiry);
	}

	@Mock
	private RecordCache recordCache;
	@Mock
	private ExpiryManager expiries;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private ExpiringEntity expiringEntity;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;

	private TxnAwareRecordsHistorian subject;

	@BeforeEach
	void setUp() {
		subject = new TxnAwareRecordsHistorian(recordCache, txnCtx, expiries);
		subject.setCreator(creator);
	}

	@Test
	void lastAddedIsEmptyAtFirst() {
		assertNull(subject.lastCreatedTopLevelRecord());
	}

	@Test
	void revertsRecordsFromGivenSourceOnly() {
		final var childRecordFrom1 = mock(ExpirableTxnRecord.Builder.class);
		final var childRecordFrom2 = mock(ExpirableTxnRecord.Builder.class);

		subject.trackChildRecord(1, childRecordFrom1, Transaction.getDefaultInstance());
		subject.trackChildRecord(2, childRecordFrom2, Transaction.getDefaultInstance());
		subject.revertChildRecordsFromSource(2);

		verify(childRecordFrom1, never()).revert();
		verify(childRecordFrom2).revert();
	}

	@Test
	void incorporatesChildRecordsIfPresent() {
		final var successfulReceipt = TxnReceipt.newBuilder().setStatus("SUCCESS").build();
		final var mockChildRecord = mock(ExpirableTxnRecord.class);
		final var mockTopLevelRecord = mock(ExpirableTxnRecord.class);
		given(mockTopLevelRecord.getReceipt()).willReturn(successfulReceipt);
		final var topLevelRecord = mock(ExpirableTxnRecord.Builder.class);
		final var childRecord = mock(ExpirableTxnRecord.Builder.class);
		final var expectedChildTime = now.plusNanos(1);

		givenTopLevelContext();
		given(topLevelRecord.build()).willReturn(mockTopLevelRecord);
		given(childRecord.build()).willReturn(mockChildRecord);
		given(mockChildRecord.asGrpc()).willReturn(TransactionRecord.getDefaultInstance());
		given(txnCtx.recordSoFar()).willReturn(topLevelRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				mockTopLevelRecord,
				nows,
				submittingMember)).willReturn(mockTopLevelRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				mockChildRecord,
				nows,
				submittingMember)).willReturn(mockChildRecord);

		subject.trackChildRecord(1, childRecord, Transaction.getDefaultInstance());

		subject.saveExpirableTransactionRecords();
		final var childRsos = subject.getChildRecords();

		verify(topLevelRecord).excludeHbarChangesFrom(childRecord);
		verify(childRecord).setConsensusTime(RichInstant.fromJava(expectedChildTime));
		assertEquals(1, childRsos.size());
		final var childRso = childRsos.get(0);
		assertSame(TransactionRecord.getDefaultInstance(), childRso.getTransactionRecord());
		assertEquals(expectedChildTime, childRso.getTimestamp());
		assertSame(Transaction.getDefaultInstance(), childRso.getTransaction());
		// and:
		verify(creator).saveExpiringRecord(effPayer, mockTopLevelRecord, nows, submittingMember);
		verify(creator).saveExpiringRecord(effPayer, mockChildRecord, nows, submittingMember);
	}

	@Test
	void addsPayerRecord() {
		givenTopLevelContext();
		given(txnCtx.recordSoFar()).willReturn(jFinalRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				finalRecord.build(),
				nows,
				submittingMember)).willReturn(payerRecord);

		final var builtFinal = finalRecord.build();

		subject.saveExpirableTransactionRecords();

		verify(txnCtx).recordSoFar();
		verify(recordCache).setPostConsensus(
				txnIdA,
				ResponseCodeEnum.valueOf(builtFinal.getReceipt().getStatus()),
				payerRecord);
		verify(creator).saveExpiringRecord(effPayer, builtFinal, nows, submittingMember);
		assertEquals(builtFinal, subject.lastCreatedTopLevelRecord());
	}

	@Test
	void hasStreamableChildrenOnlyAfterSaving() {
		assertFalse(subject.hasChildRecords());

		subject.trackChildRecord(1, ExpirableTxnRecord.newBuilder(), Transaction.getDefaultInstance());

		assertFalse(subject.hasChildRecords());

		subject.clearHistory();

		assertFalse(subject.hasChildRecords());
	}

	@Test
	void childRecordIdsIncrementThenReset() {
		assertEquals(1, subject.nextChildRecordSourceId());
		assertEquals(2, subject.nextChildRecordSourceId());

		subject.clearHistory();

		assertEquals(1, subject.nextChildRecordSourceId());
	}

	@Test
	@SuppressWarnings("unchecked")
	void tracksExpiringEntities() {
		final Consumer<EntityId> mockConsumer = mock(Consumer.class);
		given(expiringEntity.id()).willReturn(aEntity);
		given(expiringEntity.consumer()).willReturn(mockConsumer);
		given(expiringEntity.expiry()).willReturn(nows);
		given(txnCtx.expiringEntities()).willReturn(Collections.singletonList(expiringEntity));

		// when:
		subject.noteNewExpirationEvents();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity).id();
		verify(expiringEntity).consumer();
		verify(expiringEntity).expiry();
		// and:
		verify(expiries).trackExpirationEvent(Pair.of(aEntity.num(), mockConsumer), nows);
	}

	@Test
	void doesNotTrackExpiringEntity() {
		given(txnCtx.expiringEntities()).willReturn(Collections.emptyList());

		// when:
		subject.noteNewExpirationEvents();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity, never()).id();
		verify(expiringEntity, never()).consumer();
		verify(expiringEntity, never()).expiry();
		// and:
		verify(expiries, never()).trackExpirationEvent(any(), anyLong());
	}

	private void givenTopLevelContext() {
		given(accessor.getTxnId()).willReturn(txnIdA);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(txnCtx.effectivePayer()).willReturn(effPayer);
	}
}
