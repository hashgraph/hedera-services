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
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
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

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
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
	final private AccountID b = asAccount("0.0.2222");
	final private AccountID c = asAccount("0.0.3333");
	final private AccountID effPayer = asAccount("0.0.5555");
	final private long nows = 1_234_567L;
	final private int nanos = 999_999_999;
	final private Instant topLevelNow = Instant.ofEpochSecond(nows, 999_999_999);
	final int payerRecordTtl = 180;
	final long payerExpiry = topLevelNow.getEpochSecond() + payerRecordTtl;
	final private AccountID d = asAccount("0.0.4444");
	final private TransactionID txnIdA = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder()
					.setSeconds(nows)
					.setNanos(nanos))
			.setAccountID(a)
			.build();
	final private TransferList initialTransfers = withAdjustments(
			a, -1_000L, b, 500L, c, 501L, d, -1L);
	final private ExpirableTxnRecord.Builder finalRecord = ExpirableTxnRecord.newBuilder()
			.setTxnId(TxnId.fromGrpc(txnIdA))
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
		final var followingRecordFrom1 = mock(ExpirableTxnRecord.Builder.class);
		final var followingRecordFrom2 = mock(ExpirableTxnRecord.Builder.class);
		final var precedingRecordFrom1 = mock(ExpirableTxnRecord.Builder.class);
		final var precedingRecordFrom2 = mock(ExpirableTxnRecord.Builder.class);

		subject.trackFollowingChildRecord(1, TransactionBody.newBuilder(), followingRecordFrom1);
		subject.trackFollowingChildRecord(2, TransactionBody.newBuilder(), followingRecordFrom2);
		subject.trackPrecedingChildRecord(1, TransactionBody.newBuilder(), precedingRecordFrom1);
		subject.trackPrecedingChildRecord(2, TransactionBody.newBuilder(), precedingRecordFrom2);
		subject.revertChildRecordsFromSource(2);

		verify(followingRecordFrom1, never()).revert();
		verify(followingRecordFrom2).revert();
		verify(precedingRecordFrom1, never()).revert();
		verify(precedingRecordFrom2).revert();
	}

	@Test
	void incorporatesChildRecordsIfPresent() {
		final var mockFollowingRecord = mock(ExpirableTxnRecord.class);
		final var followingChildNows = nows + 1;
		given(mockFollowingRecord.getConsensusSecond()).willReturn(followingChildNows);
		final var mockPrecedingRecord = mock(ExpirableTxnRecord.class);
		final var precedingChildNows = nows - 1;
		given(mockPrecedingRecord.getConsensusSecond()).willReturn(precedingChildNows);
		given(mockPrecedingRecord.getEnumStatus()).willReturn(INVALID_ACCOUNT_ID);
		given(mockFollowingRecord.getEnumStatus()).willReturn(INVALID_CHUNK_NUMBER);

		final var expPrecedeId = txnIdA.toBuilder().setNonce(1).build();
		final var expFollowId = txnIdA.toBuilder().setNonce(2).build();

		final var expectedPrecedingChildId = new TxnId(
				aEntity, new RichInstant(nows, nanos), false, 1);
		final var expectedFollowingChildId = new TxnId(
				aEntity, new RichInstant(nows, nanos), false, 2);

		final var mockTopLevelRecord = mock(ExpirableTxnRecord.class);
		given(mockTopLevelRecord.getEnumStatus()).willReturn(SUCCESS);

		final var topLevelRecord = mock(ExpirableTxnRecord.Builder.class);
		given(topLevelRecord.getTxnId()).willReturn(TxnId.fromGrpc(txnIdA));
		final var followingBuilder = mock(ExpirableTxnRecord.Builder.class);
		given(followingBuilder.getTxnId()).willReturn(expectedFollowingChildId);
		given(mockFollowingRecord.getTxnId()).willReturn(expectedFollowingChildId);
		final var precedingBuilder = mock(ExpirableTxnRecord.Builder.class);
		given(precedingBuilder.getTxnId()).willReturn(expectedPrecedingChildId);
		given(mockPrecedingRecord.getTxnId()).willReturn(expectedPrecedingChildId);
		final var expectedFollowTime = topLevelNow.plusNanos(1);
		final var expectedPrecedingTime = topLevelNow.minusNanos(1);

		givenTopLevelContext();
		given(topLevelRecord.setNumChildRecords(anyShort())).willReturn(topLevelRecord);
		given(topLevelRecord.build()).willReturn(mockTopLevelRecord);
		given(followingBuilder.build()).willReturn(mockFollowingRecord);
		given(precedingBuilder.build()).willReturn(mockPrecedingRecord);

		given(txnCtx.recordSoFar()).willReturn(topLevelRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				mockTopLevelRecord,
				nows,
				submittingMember)).willReturn(mockTopLevelRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				mockFollowingRecord,
				followingChildNows,
				submittingMember)).willReturn(mockFollowingRecord);
		given(creator.saveExpiringRecord(
				effPayer,
				mockPrecedingRecord,
				precedingChildNows,
				submittingMember)).willReturn(mockPrecedingRecord);

		final var followSynthBody = aBuilderWith("FOLLOW");
		final var precedeSynthBody = aBuilderWith("PRECEDE");
		assertEquals(topLevelNow.plusNanos(1), subject.nextFollowingChildConsensusTime());
		subject.trackFollowingChildRecord(1, followSynthBody, followingBuilder);
		assertEquals(topLevelNow.plusNanos(2), subject.nextFollowingChildConsensusTime());
		subject.trackPrecedingChildRecord(1, precedeSynthBody, precedingBuilder);

		subject.saveExpirableTransactionRecords();
		final var followingRsos = subject.getFollowingChildRecords();
		final var precedingRsos = subject.getPrecedingChildRecords();

		verify(topLevelRecord).excludeHbarChangesFrom(followingBuilder);
		verify(topLevelRecord).excludeHbarChangesFrom(precedingBuilder);
		verify(topLevelRecord).setNumChildRecords((short) 2);
		verify(followingBuilder).setConsensusTime(RichInstant.fromJava(expectedFollowTime));
		verify(followingBuilder).setParentConsensusTime(topLevelNow);
		verify(precedingBuilder).setConsensusTime(RichInstant.fromJava(expectedPrecedingTime));
		verify(precedingBuilder).setTxnId(expectedPrecedingChildId);
		verify(followingBuilder).setTxnId(expectedFollowingChildId);
		assertEquals(1, followingRsos.size());
		assertEquals(1, precedingRsos.size());

		final var precedeRso = precedingRsos.get(0);
		assertEquals(expectedPrecedingTime, precedeRso.getTimestamp());
		final var precedeSynth = precedeRso.getTransaction();
		final var expectedPrecedeSynth = synthFromBody(
				precedeSynthBody
						.setTransactionID(expPrecedeId)
						.build());
		assertEquals(expectedPrecedeSynth, precedeSynth);

		final var followRso = followingRsos.get(0);
		assertEquals(expectedFollowTime, followRso.getTimestamp());
		final var followSynth = followRso.getTransaction();
		final var expectedFollowSynth = synthFromBody(
				followSynthBody
						.setTransactionID(expFollowId)
						.build());
		assertEquals(expectedFollowSynth, followSynth);

		verify(creator).saveExpiringRecord(effPayer, mockPrecedingRecord, precedingChildNows, submittingMember);
		verify(creator).saveExpiringRecord(effPayer, mockTopLevelRecord, nows, submittingMember);
		verify(creator).saveExpiringRecord(effPayer, mockFollowingRecord, followingChildNows, submittingMember);
		verifyBuilderUse(
				precedingBuilder,
				expectedPrecedingChildId,
				noThrowSha384HashOf(expectedPrecedeSynth.getSignedTransactionBytes().toByteArray()));
		verifyBuilderUse(
				followingBuilder,
				expectedFollowingChildId,
				noThrowSha384HashOf(expectedFollowSynth.getSignedTransactionBytes().toByteArray()));
		verify(recordCache).setPostConsensus(expPrecedeId, INVALID_ACCOUNT_ID, mockPrecedingRecord);
		verify(recordCache).setPostConsensus(expFollowId, INVALID_CHUNK_NUMBER, mockFollowingRecord);
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
		assertFalse(subject.hasPrecedingChildRecords());
		assertFalse(subject.hasFollowingChildRecords());

		subject.trackFollowingChildRecord(1, TransactionBody.newBuilder(), ExpirableTxnRecord.newBuilder());
		subject.trackPrecedingChildRecord(1, TransactionBody.newBuilder(), ExpirableTxnRecord.newBuilder());

		assertFalse(subject.hasFollowingChildRecords());
		assertFalse(subject.hasPrecedingChildRecords());

		subject.clearHistory();

		assertFalse(subject.hasFollowingChildRecords());
		assertFalse(subject.hasPrecedingChildRecords());
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
		given(txnCtx.consensusTime()).willReturn(topLevelNow);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(txnCtx.effectivePayer()).willReturn(effPayer);
	}

	private Transaction synthFromBody(final TransactionBody txnBody) {
		final var signedTxn = SignedTransaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build();
		return Transaction.newBuilder()
				.setSignedTransactionBytes(signedTxn.toByteString())
				.build();
	}

	private TransactionBody.Builder aBuilderWith(final String memo) {
		return TransactionBody.newBuilder().setMemo(memo);
	}

	private void verifyBuilderUse(
			final ExpirableTxnRecord.Builder builder,
			final TxnId expectedId,
			final byte[] expectedHash
	) {
		verify(builder).setTxnId(expectedId);
		verify(builder).setTxnHash(expectedHash);
	}
}
