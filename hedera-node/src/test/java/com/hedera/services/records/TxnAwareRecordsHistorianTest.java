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
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

class TxnAwareRecordsHistorianTest {
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
	final private ExpirableTxnRecord finalRecord = ExpirableTxnRecord.newBuilder()
			.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder().setAccountID(a).build()))
			.setTransferList(CurrencyAdjustments.fromGrpc(initialTransfers))
			.setMemo("This is different!")
			.setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build())
			.build();
	final private ExpirableTxnRecord jFinalRecord = finalRecord;
	{
		jFinalRecord.setExpiry(expiry);
	}
	final private ExpirableTxnRecord payerRecord = finalRecord;
	{
		payerRecord.setExpiry(payerExpiry);
	}

	private RecordCache recordCache;
	private ExpiryManager expiries;
	private GlobalDynamicProperties dynamicProperties;
	private ExpiringCreations creator;
	private ExpiringEntity expiringEntity;
	private TransactionContext txnCtx;
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	private TxnAwareRecordsHistorian subject;

	@Test
	void lastAddedIsEmptyAtFirst() {
		setupForAdd();

		// expect:
		assertNull(subject.lastCreatedTopLevelRecord());
	}

	@Test
	void addsRecordToAllQualifyingAccounts() {
		setupForAdd();

		// when:
		subject.finalizeExpirableTransactionRecords();
		subject.saveExpirableTransactionRecords();

		// then:
		verify(txnCtx).recordSoFar();
		verify(recordCache).setPostConsensus(
				txnIdA,
				ResponseCodeEnum.valueOf(finalRecord.getReceipt().getStatus()),
				payerRecord);
		verify(creator).saveExpiringRecord(effPayer, finalRecord, nows, submittingMember);
		// and:
		assertEquals(finalRecord, subject.lastCreatedTopLevelRecord());
	}

	@Test
	void managesAddNewEntities() {
		setupForAdd();

		// when:
		subject.noteNewExpirationEvents();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity).id();
		verify(expiringEntity).consumer();
		verify(expiringEntity).expiry();
		// and:
		verify(expiries).trackExpirationEvent(any(), eq(nows));
	}

	@Test
	void doesNotTrackExpiringEntity() {
		setupForAdd();
		given(txnCtx.expiringEntities()).willReturn(Collections.EMPTY_LIST);

		// when:
		subject.noteNewExpirationEvents();

		// then:
		verify(txnCtx).expiringEntities();
		verify(expiringEntity, never()).id();
		verify(expiringEntity, never()).consumer();
		verify(expiringEntity, never()).expiry();
		// and:
		verify(expiries, never()).trackExpirationEvent(any(), eq(nows));
	}

	private void setupForReview() {
		setupForAdd();
	}

	private void setupForAdd() {
		expiries = mock(ExpiryManager.class);

		dynamicProperties = mock(GlobalDynamicProperties.class);
		given(dynamicProperties.fundingAccount()).willReturn(funding);

		creator = mock(ExpiringCreations.class);
		given(creator.saveExpiringRecord(effPayer, finalRecord, nows, submittingMember)).willReturn(payerRecord);

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
		given(txnCtx.recordSoFar()).willReturn(jFinalRecord);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(txnCtx.effectivePayer()).willReturn(effPayer);
		given(txnCtx.expiringEntities()).willReturn(Collections.singletonList(expiringEntity));

		accounts = mock(MerkleMap.class);

		recordCache = mock(RecordCache.class);

		subject = new TxnAwareRecordsHistorian(
				recordCache,
				txnCtx,
				expiries);
		subject.setCreator(creator);
	}
}
