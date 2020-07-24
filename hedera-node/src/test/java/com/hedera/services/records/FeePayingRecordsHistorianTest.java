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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.fees.charging.ItemizableFeeCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.legacy.core.jproto.TxnId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Collections.EMPTY_LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.services.ledger.properties.AccountProperty.*;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RunWith(JUnitPlatform.class)
public class FeePayingRecordsHistorianTest {
	final private AccountID sn = asAccount("0.0.3");
	final private long submittingMember = 1L;
	final private AccountID a = asAccount("0.0.1111");
	final private TransactionID txnIdA = TransactionID.newBuilder().setAccountID(a).build();
	final private AccountID b = asAccount("0.0.2222");
	final private TransactionID txnIdB = TransactionID.newBuilder().setAccountID(b).build();
	final private AccountID c = asAccount("0.0.3333");
	final private TransactionID txnIdC = TransactionID.newBuilder().setAccountID(c).build();
	final private long recordFee = 1_230L;
	final private Instant now = Instant.now();
	final private long nows = now.getEpochSecond();
	final private long lastCons = 100L;
	final private int cacheTtl = 30;
	final int accountRecordTtl = 1_000;
	final int payerRecordTtl = 180;
	final long expiry = now.getEpochSecond() + accountRecordTtl;
	final long payerExpiry = now.getEpochSecond() + payerRecordTtl;
	final private List<Long> aExps = List.of(expiry + 55L);
	final private List<Long> aCons = List.of(lastCons - cacheTtl);
	final private List<TransactionID> aIds = List.of(txnIdA);
	final private long aBalance = recordFee - 1L;
	final private long aSendThresh = 999L;
	final private long aReceiveThresh = 1_001L;
	final private EarliestRecordExpiry aERE = new EarliestRecordExpiry(nows - 100L, a);
	final private MerkleEntityId aKey = MerkleEntityId.fromPojoAccountId(a);
	final private List<Long> bExps = List.of(expiry - 55L);
	final private List<Long> bCons = List.of(lastCons - cacheTtl - 1);
	final private List<TransactionID> bIds = List.of(txnIdB);
	final private long bBalance = recordFee + 1L;
	final private long bSendThresh = 2_000L;
	final private long bReceiveThresh = 201L;
	final private EarliestRecordExpiry bERE = new EarliestRecordExpiry(nows - 55L, b);
	final private MerkleEntityId bKey = MerkleEntityId.fromPojoAccountId(b);
	final private List<Long> cExps = List.of();
	final private long cBalance = recordFee + 1L;
	final private long cSendThresh = 3_000L;
	final private long cReceiveThresh = 301L;
	final private MerkleEntityId cKey = MerkleEntityId.fromPojoAccountId(c);
	final private EarliestRecordExpiry cERE = new EarliestRecordExpiry(nows + 50L, c);
	final private List<Long> dExps = List.of();
	final private long dBalance = recordFee + 1L;
	final private long snBalance = 1_234_567_890L;
	final private long dSendThresh = 0L;
	final private long dReceiveThresh = 50_000_000_000L;
	final private long cacheRecordFee = 10 * recordFee;
	final private AccountID d = asAccount("0.0.4444");
	final private String contract = "1.2.3";
	final private String duplicateContract = "0.0.3333";
	final private AccountID funding = asAccount("0.0.98");
	final private TransferList initialTransfers = withAdjustments(
			a, -1_000L, b, 500L, c, 501L, d, -1L);
	final private TransactionRecord record = TransactionRecord.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(a))
			.setTransferList(initialTransfers)
			.build();
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

	private MerkleAccount aValue;
	private MerkleAccount bValue;
	private MerkleAccount cValue;
	private MerkleAccount dValue;
	private MerkleAccount snValue;
	private RecordCache recordCache;
	private HederaLedger ledger;
	private FeeCalculator fees;
	private FeeExemptions exemptions;
	private PropertySource properties;
	private ExpiringCreations creator;
	private TransactionContext txnCtx;
	private ItemizableFeeCharging itemizableFeeCharging;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private BlockingQueue<EarliestRecordExpiry> expirations;

	private FeePayingRecordsHistorian subject;
	private Predicate<TransactionContext> IS_QUERYABLE;

	@Test
	public void doesntAddRecordToCreatedContractIfAlreadyDoneViaThreshX() {
		setupForAdd();
		addSetupForValidContractCreate(duplicateContract);

		// when:
		subject.addNewRecords();

		// then:
		verify(creator).createExpiringHistoricalRecord(
				argThat(asAccount(duplicateContract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void addsRecordToCreatedContract() {
		setupForAdd();
		addSetupForValidContractCreate();

		// when:
		subject.addNewRecords();

		// then:
		verify(creator).createExpiringHistoricalRecord(
				argThat(asAccount(contract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void addsRecordToCalledContract() {
		setupForAdd();
		addSetupForValidContractCall();

		// when:
		subject.addNewRecords();

		// then:
		verify(creator).createExpiringHistoricalRecord(
				argThat(asAccount(contract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
		verify(txnCtx, times(2)).recordSoFar();
	}

	@Test
	public void doesntAddRecordToDeletedCalledContract() {
		setupForAdd();
		addSetupForCallToDeletedContract();

		// when:
		subject.addNewRecords();

		// then:
		verify(creator, never()).createExpiringHistoricalRecord(
				argThat(asAccount(contract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void doesntAddRecordToCalledAccount() {
		setupForAdd();
		addSetupForCallToAccount();

		// when:
		subject.addNewRecords();

		// then:
		verify(creator, never()).createExpiringHistoricalRecord(
				argThat(asAccount(contract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void doesntAddRecordToContractIfStatusNotSuccess() {
		setupForAdd();
		addSetupForValidContractCreate();
		given(txnCtx.status()).willReturn(FAIL_INVALID);

		// when:
		subject.addNewRecords();

		// then:
		verify(creator, never()).createExpiringHistoricalRecord(
				argThat(asAccount(contract)::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void lastAddedIsEmptyAtFirst() {
		setupForAdd();

		// expect:
		assertFalse(subject.lastCreatedRecord().isPresent());
	}

	@Test
	public void doesntChargeRecordCachingFeeIfRecordNotQueryable() {
		setupForAdd();
		given(IS_QUERYABLE.test(any())).willReturn(false);

		// when:
		subject.addNewRecords();

		// then:
		verify(ledger, never()).doTransfer(a, funding, aBalance);
	}

	@Test
	public void doesntCacheNonqueryableRecord() {
		setupForAdd();
		given(IS_QUERYABLE.test(any())).willReturn(false);

		// when:
		subject.addNewRecords();

		// then:
		verify(recordCache, never()).setPostConsensus(
				txnIdA,
				finalRecord.getReceipt().getStatus(),
				null,
				submittingMember);
	}

	@Test
	public void skipsAccountsPendingCreation() {
		setupForAdd();
		given(ledger.isPendingCreation(b)).willReturn(true);

		// when:
		subject.addNewRecords();

		// then:
		verify(creator, never()).createExpiringHistoricalRecord(
				argThat(b::equals),
				any(),
				longThat(l -> l == now.getEpochSecond()));
	}

	@Test
	public void usesActivePayerForCachePayment() {
		setupForAdd();

		given(txnCtx.isPayerSigKnownActive()).willReturn(false);

		// when:
		subject.addNewRecords();

		// then:
		verify(txnCtx).isPayerSigKnownActive();
		verify(ledger).doTransfer(sn, funding, cacheRecordFee);
	}

	@Test
	public void addsRecordToEffectivePayer() {
		setupForAdd();

		// when:
		subject.addNewRecords();

		// then:
		/* TODO */
	}

	@Test
	public void addsRecordToQualifyingThresholdAccounts() {
		setupForAdd();

		// when:
		subject.addNewRecords();

		// then:
		verify(exemptions).isExemptFromFees(txnCtx.accessor().getTxn());
		verify(fees).computeCachingFee(record);
		verify(recordCache).setPostConsensus(
				txnIdA,
				finalRecord.getReceipt().getStatus(),
				null,
				submittingMember);
		verify(ledger).doTransfer(a, funding, aBalance);
		// and:
		verify(ledger).netTransfersInTxn();
		verify(txnCtx, times(2)).recordSoFar();
		verify(fees).computeStorageFee(record);
		// and:
		verify(ledger, times(2)).getBalance(a);
		verify(ledger).getBalance(b);
		verify(ledger).fundsReceivedRecordThreshold(b);
		verify(ledger).getBalance(c);
		verify(ledger).getBalance(d);
		verify(ledger).fundsSentRecordThreshold(d);
		// and:
		verify(properties, times(1)).getAccountProperty("ledger.funding.account");
		verify(ledger).doTransfer(b, funding, recordFee);
		verify(ledger, never()).doTransfer(c, funding, recordFee);
		verify(ledger).doTransfer(d, funding, recordFee);
		// and:
		verify(properties, never()).getIntProperty("ledger.records.ttl");
		verify(txnCtx, times(1)).consensusTime();
		// and:
		verify(creator).createExpiringHistoricalRecord(b, finalRecord, now.getEpochSecond());
		verify(creator).createExpiringHistoricalRecord(c, finalRecord, now.getEpochSecond());
		verify(creator).createExpiringHistoricalRecord(d, finalRecord, now.getEpochSecond());
		verify(creator, never()).createExpiringHistoricalRecord(asAccount(contract), finalRecord, now.getEpochSecond());
		verify(ledger, never()).addRecord(b, jFinalRecord);
		verify(expirations, never()).offer(new EarliestRecordExpiry(expiry, b));
		verify(ledger, never()).addRecord(c, jFinalRecord);
		verify(expirations, never()).offer(new EarliestRecordExpiry(expiry, c));
		verify(ledger, never()).addRecord(d, jFinalRecord);
		verify(expirations, never()).offer(new EarliestRecordExpiry(expiry, d));
		verify(ledger, never()).addRecord(asAccount(contract), jFinalRecord);
		// and:
		assertEquals(finalRecord, subject.lastCreatedRecord().get());
	}

	@Test
	public void managesReviewCorrectly() {
		setupForReview();
		given(accounts.entrySet()).willReturn(Set.of(
				new AbstractMap.SimpleEntry<>(aKey, aValue),
				new AbstractMap.SimpleEntry<>(bKey, bValue),
				new AbstractMap.SimpleEntry<>(cKey, cValue)));

		// when:
		subject.reviewExistingRecords(lastCons);

		// then:
		verify(expirations).offer(new EarliestRecordExpiry(expiry + 55L, a));
		verify(expirations).offer(new EarliestRecordExpiry(expiry - 55L, b));
	}

	@Test
	public void managesExpirationsCorrectly() {
		setupForPurge();
		InOrder inOrder = inOrder(txnCtx, ledger);

		// given:
		subject.purgeExpiredRecords();

		// expect:
		List<EarliestRecordExpiry> remaining = new ArrayList<>();
		expirations.drainTo(remaining);
		assertThat(remaining, contains(cERE, new EarliestRecordExpiry(nows + 55L, b)));
		// and:
		inOrder.verify(txnCtx).consensusTime();
		inOrder.verify(ledger).purgeExpiredRecords(a, nows);
		inOrder.verify(ledger).purgeExpiredRecords(b, nows);
		inOrder.verify(ledger, never()).purgeExpiredRecords(c, nows);
	}

	private void addSetupForCallToAccount() {
		ContractCallTransactionBody op = mock(ContractCallTransactionBody.class);
		given(op.getContractID()).willReturn(asContract(contract));
		TransactionBody contractCallTxn = mock(TransactionBody.class);
		given(contractCallTxn.hasContractCall()).willReturn(true);
		given(contractCallTxn.getContractCall()).willReturn(op);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(accessor.getTxnId()).willReturn(txnIdA);

		given(txnCtx.accessor()).willReturn(accessor);

		MerkleAccount v = new MerkleAccount();
		IS_SMART_CONTRACT.setter().accept(v, false);
		given(accounts.get(MerkleEntityId.fromPojoAccountId(asAccount(contract)))).willReturn(v);
	}

	private void addSetupForCallToDeletedContract() {
		ContractCallTransactionBody op = mock(ContractCallTransactionBody.class);
		given(op.getContractID()).willReturn(asContract(contract));
		TransactionBody contractCallTxn = mock(TransactionBody.class);
		given(contractCallTxn.hasContractCall()).willReturn(true);
		given(contractCallTxn.getContractCall()).willReturn(op);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(accessor.getTxnId()).willReturn(txnIdA);

		given(txnCtx.accessor()).willReturn(accessor);

		MerkleAccount v = new MerkleAccount();
		IS_SMART_CONTRACT.setter().accept(v, true);
		IS_DELETED.setter().accept(v, true);
		given(accounts.get(MerkleEntityId.fromPojoAccountId(asAccount(contract)))).willReturn(v);
	}

	private void addSetupForValidContractCall() {
		ContractCallTransactionBody op = mock(ContractCallTransactionBody.class);
		given(op.getContractID()).willReturn(asContract(contract));
		TransactionBody contractCallTxn = mock(TransactionBody.class);
		given(contractCallTxn.hasContractCall()).willReturn(true);
		given(contractCallTxn.getContractCall()).willReturn(op);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(accessor.getTxnId()).willReturn(txnIdA);
		given(accessor.getPayer()).willReturn(a);
		given(ledger.netTransfersInTxn()).willReturn(TransferList.getDefaultInstance());

		given(txnCtx.accessor()).willReturn(accessor);

		MerkleAccount v = new MerkleAccount();
		IS_SMART_CONTRACT.setter().accept(v, true);
		given(accounts.get(MerkleEntityId.fromPojoAccountId(asAccount(contract)))).willReturn(v);
	}

	private void addSetupForValidContractCreate() {
		addSetupForValidContractCreate(contract);
	}

	private void addSetupForValidContractCreate(String contractToUse) {
		TransactionBody contractCreateTxn = mock(TransactionBody.class);
		given(contractCreateTxn.hasContractCreateInstance()).willReturn(true);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(accessor.getTxnId()).willReturn(txnIdA);

		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder().setContractID(asContract(contractToUse)))
				.build();
		given(txnCtx.recordSoFar()).willReturn(record);
		given(txnCtx.status()).willReturn(ResponseCodeEnum.SUCCESS);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void setupForReview() {
		setupForAdd();
	}

	private void setupForAdd() {
		IS_QUERYABLE = mock(Predicate.class);
		given(IS_QUERYABLE.test(any())).willReturn(true);

		ledger = mock(HederaLedger.class);
		given(ledger.netTransfersInTxn()).willReturn(initialTransfers);
		given(ledger.addRecord(any(), any())).willReturn(expiry);
		given(ledger.isPendingCreation(any())).willReturn(false);

		fees = mock(FeeCalculator.class);
		given(fees.computeCachingFee(any())).willReturn(cacheRecordFee);
		given(fees.computeStorageFee(any())).willReturn(recordFee);

		exemptions = mock(FeeExemptions.class);
		given(exemptions.isExemptFromRecordFees(c)).willReturn(true);

		properties = mock(PropertySource.class);
		given(properties.getAccountProperty("ledger.funding.account")).willReturn(funding);
		given(properties.getIntProperty("ledger.records.ttl")).willReturn(accountRecordTtl);

		creator = mock(ExpiringCreations.class);

		TransactionBody txn = mock(TransactionBody.class);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getTxnId()).willReturn(txnIdA);
		given(accessor.getPayer()).willReturn(a);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.status()).willReturn(SUCCESS);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnCtx.recordSoFar()).willReturn(record).willReturn(finalRecord);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);

		accounts = mock(FCMap.class);
		aValue = add(a, aBalance, aSendThresh, aReceiveThresh, aExps, aCons, aIds);
		bValue = add(b, bBalance, bSendThresh, bReceiveThresh, bExps, bCons, bIds);
		cValue = add(c, cBalance, cSendThresh, cReceiveThresh, cExps, EMPTY_LIST, EMPTY_LIST);
		dValue = add(d, dBalance, dSendThresh, dReceiveThresh, dExps, EMPTY_LIST, EMPTY_LIST);
		snValue = add(sn, snBalance, dSendThresh, dReceiveThresh, EMPTY_LIST, EMPTY_LIST, EMPTY_LIST);

		itemizableFeeCharging = new ItemizableFeeCharging(exemptions, properties);
		itemizableFeeCharging.resetFor(accessor, sn);

		expirations = mock(BlockingQueue.class);

		recordCache = mock(RecordCache.class);

		subject = new FeePayingRecordsHistorian(
				recordCache,
				fees,
				properties,
				txnCtx,
				itemizableFeeCharging,
				accounts,
				IS_QUERYABLE,
				expirations);
		subject.setLedger(ledger);
		subject.setCreator(creator);
	}

	private void setupForPurge() {
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(now);

		ledger = mock(HederaLedger.class);
		given(ledger.purgeExpiredRecords(a, nows)).willReturn(-1L);
		given(ledger.purgeExpiredRecords(c, nows)).willReturn(-1L);
		given(ledger.purgeExpiredRecords(b, nows)).willReturn(nows + 55L);

		expirations = new PriorityBlockingQueue<>();
		expirations.offer(cERE);
		expirations.offer(bERE);
		expirations.offer(aERE);

		itemizableFeeCharging = new ItemizableFeeCharging(exemptions, properties);

		subject = new FeePayingRecordsHistorian(
				recordCache,
				fees,
				properties,
				txnCtx,
				itemizableFeeCharging,
				accounts,
				IS_QUERYABLE,
				expirations);
		subject.setLedger(ledger);
	}

	private MerkleAccount add(
			AccountID id,
			long balance,
			long sendThreshold,
			long receiveThreshold,
			List<Long> expiries,
			List<Long> consensusTimes,
			List<TransactionID> txnIds
	) {
		MerkleEntityId key = MerkleEntityId.fromPojoAccountId(id);
		MerkleAccount value = new MerkleAccount();
		given(ledger.getBalance(id)).willReturn(balance);
		given(ledger.fundsSentRecordThreshold(id)).willReturn(sendThreshold);
		given(ledger.fundsReceivedRecordThreshold(id)).willReturn(receiveThreshold);
		FUNDS_SENT_RECORD_THRESHOLD.setter().accept(value, 50_000_000_000L);
		FUNDS_RECEIVED_RECORD_THRESHOLD.setter().accept(value, 50_000_000_000L);
		HISTORY_RECORDS.setter().accept(
				value,
				asFcq(IntStream.range(0, expiries.size()).mapToObj(i -> {
					ExpirableTxnRecord record = new ExpirableTxnRecord(
						null,
						new byte[0],
						TxnId.fromGrpc(txnIds.get(i)),
						RichInstant.fromGrpc(Timestamp.newBuilder().setSeconds(consensusTimes.get(i)).build()),
						"",
						0L,
						null,
						null,
						null);
					record.setExpiry(expiries.get(i));
					return record;
				}).collect(Collectors.toCollection(LinkedList::new))));
		given(accounts.get(key)).willReturn(value);
		return value;
	}

	private FCQueue<ExpirableTxnRecord> asFcq(LinkedList<ExpirableTxnRecord> ll) {
		FCQueue<ExpirableTxnRecord> fcq = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
		for (ExpirableTxnRecord record : ll) {
			fcq.offer(record);
		}
		return fcq;
	}
}
