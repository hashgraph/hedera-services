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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

public class TxnAwareRecordsHistorianTest {
	final private long submittingMember = 1L;
	final private AccountID a = asAccount("0.0.1111");
	final private EntityId aEntity = EntityId.fromGrpcAccountId(a);
	final private TransactionID txnIdA = TransactionID.newBuilder().setAccountID(a).build();
	final private AccountID b = asAccount("0.0.2222");
	final private AccountID c = asAccount("0.0.3333");
	final private AccountID effPayer = asAccount("0.0.13257");
	final private Instant now = Instant.now();
	final private long nows = now.getEpochSecond();
	final int accountRecordTtl = 1_000;
	final int payerRecordTtl = 180;
	final long expiry = now.getEpochSecond() + accountRecordTtl;
	final long payerExpiry = now.getEpochSecond() + payerRecordTtl;
	final private AccountID d = asAccount("0.0.4444");
	final private AccountID funding = asAccount("0.0.98");
	final private TransferList initialTransfers = withAdjustments(
			a, -1_000L, b, 500L, c, 501L, d, -1L);
	final private TransactionRecord finalRecord = TransactionRecord.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(a))
			.setTransferList(initialTransfers)
			.setMemo("This is different!")
			.build();
	final private ExpirableTxnRecord jFinalRecord = ExpirableTxnRecord.fromGprc(finalRecord);
	{
		jFinalRecord.setExpiry(expiry);
	}
	final private ExpirableTxnRecord payerRecord = ExpirableTxnRecord.fromGprc(finalRecord);
	{
		payerRecord.setExpiry(payerExpiry);
	}

	private RecordCache recordCache;
	private HederaLedger ledger;
	private ExpiryManager expiries;
	private GlobalDynamicProperties dynamicProperties;
	private ExpiringCreations creator;
	private ExpiringEntity expiringEntity;
	private TransactionContext txnCtx;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private TxnAwareRecordsHistorian subject;

	@Test
	public void lastAddedIsEmptyAtFirst() {
		setupForAdd();

		// expect:
		assertFalse(subject.lastCreatedRecord().isPresent());
	}

	@Test
	public void addsRecordToAllQualifyingAccounts() {
		setupForAdd();
		given(dynamicProperties.shouldKeepRecordsInState()).willReturn(true);

		// when:
		subject.addNewRecords();

		// then:
		verify(txnCtx).recordSoFar();
		verify(recordCache).setPostConsensus(
				txnIdA,
				finalRecord.getReceipt().getStatus(),
				payerRecord);
		verify(creator).createExpiringRecord(effPayer, finalRecord, nows, submittingMember);
		// and:
		assertEquals(finalRecord, subject.lastCreatedRecord().get());
	}

	@Test
	public void managesAddNewEntities() {
		setupForAdd();

		// when:
		subject.addNewEntities();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity).id();
		verify(expiringEntity).consumer();
		verify(expiringEntity).expiry();
		// and:
		verify(expiries).trackEntity(any(), eq(nows));
	}

	@Test
	public void doesNotTrackExpiringEntity() {
		setupForAdd();
		given(txnCtx.expiringEntities()).willReturn(Collections.EMPTY_LIST);

		// when:
		subject.addNewEntities();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity, never()).id();
		verify(expiringEntity, never()).consumer();
		verify(expiringEntity, never()).expiry();
		// and:
		verify(expiries, never()).trackEntity(any(), eq(nows));
	}

	@Test
	public void managesReviewRecordsCorrectly() {
		setupForReview();

		// when:
		subject.reviewExistingRecords();

		// then:
		verify(expiries).restartTrackingFrom(accounts);
	}

	@Test
	public void managesExpiredRecordsCorrectly() {
		setupForPurge();

		// when:
		subject.purgeExpiredRecords();

		// expect:
		verify(expiries).purgeExpiredRecordsAt(nows, ledger);
	}

	private void setupForReview() {
		setupForAdd();
	}

	private void setupForAdd() {
		expiries = mock(ExpiryManager.class);

		ledger = mock(HederaLedger.class);
		given(ledger.netTransfersInTxn()).willReturn(initialTransfers);
		given(ledger.isPendingCreation(any())).willReturn(false);

		dynamicProperties = mock(GlobalDynamicProperties.class);
		given(dynamicProperties.fundingAccount()).willReturn(funding);

		creator = mock(ExpiringCreations.class);
		given(creator.createExpiringRecord(effPayer, finalRecord, nows, submittingMember)).willReturn(payerRecord);

		expiringEntity = mock(ExpiringEntity.class);
		given(expiringEntity.id()).willReturn(aEntity);
		given(expiringEntity.consumer()).willReturn(mock(Consumer.class));
		given(expiringEntity.expiry()).willReturn(nows);

		TransactionBody txn = mock(TransactionBody.class);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getTxnId()).willReturn(txnIdA);
		given(accessor.getPayer()).willReturn(a);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.status()).willReturn(SUCCESS);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnCtx.recordSoFar()).willReturn(finalRecord);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(txnCtx.effectivePayer()).willReturn(effPayer);
		given(txnCtx.expiringEntities()).willReturn(Collections.singletonList(expiringEntity));

		accounts = mock(FCMap.class);

		recordCache = mock(RecordCache.class);

		subject = new TxnAwareRecordsHistorian(
				recordCache,
				txnCtx,
				() -> accounts,
				expiries);
		subject.setLedger(ledger);
		subject.setCreator(creator);
	}

	private void setupForPurge() {
		expiries = mock(ExpiryManager.class);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(now);

		ledger = mock(HederaLedger.class);

		subject = new TxnAwareRecordsHistorian(
				recordCache,
				txnCtx,
				() -> accounts,
				expiries);
		subject.setLedger(ledger);
	}
}
