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

import com.google.common.base.Stopwatch;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.ItemizableFeeCharging;

import static com.hedera.services.fees.TxnFeeType.CACHE_RECORD;
import static com.hedera.services.fees.TxnFeeType.THRESHOLD_RECORD;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.CACHE_RECORD_FEE;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.ExpirableTxnRecord;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

import static com.hedera.services.fees.charging.ItemizableFeeCharging.THRESHOLD_RECORD_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toSet;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 *
 * @author Michael Tinker
 */
public class FeePayingRecordsHistorian implements AccountRecordsHistorian {
	private static final Logger log = LogManager.getLogger(FeePayingRecordsHistorian.class);

	private HederaLedger ledger;
	private ExpirableTxnRecord lastCreatedRecord;
	private Set<AccountID> accountsWithExpiringRecords;

	private final RecordCache recordCache;
	private final FeeCalculator fees;
	private final PropertySource properties;
	private final TransactionContext txnCtx;
	private final ItemizableFeeCharging feeCharging;
	private final FCMap<MerkleEntityId, MerkleAccount> accounts;
	private final Predicate<TransactionContext> isScopedRecordQueryable;
	private final BlockingQueue<EarliestRecordExpiry> expirations;

	public FeePayingRecordsHistorian(
			RecordCache recordCache,
			FeeCalculator fees,
			PropertySource properties,
			TransactionContext txnCtx,
			ItemizableFeeCharging feeCharging,
			FCMap<MerkleEntityId, MerkleAccount> accounts,
			Predicate<TransactionContext> isScopedRecordQueryable,
			BlockingQueue<EarliestRecordExpiry> expirations
	) {
		this.fees = fees;
		this.txnCtx = txnCtx;
		this.accounts = accounts;
		this.properties = properties;
		this.recordCache = recordCache;
		this.expirations = expirations;
		this.feeCharging = feeCharging;
		this.isScopedRecordQueryable = isScopedRecordQueryable;

		accountsWithExpiringRecords = new HashSet<>();
	}

	@Override
	public Optional<ExpirableTxnRecord> lastCreatedRecord() {
		return Optional.ofNullable(lastCreatedRecord);
	}

	@Override
	public void setLedger(HederaLedger ledger) {
		this.ledger = ledger;
		feeCharging.setLedger(ledger);
	}

	@Override
	public void addNewRecords() {
		TransactionRecord record = txnCtx.recordSoFar();
		long cachingFeePaid = isScopedRecordQueryable.test(txnCtx) ? payForCaching(record) : 0;

		long thresholdRecordFee = fees.computeStorageFee(record);
		Set<AccountID> qualifiers = getThreshXQualifiers(thresholdRecordFee);
		feeCharging.setFor(THRESHOLD_RECORD, thresholdRecordFee);
		payForThresholdRecords(qualifiers);
		if (feeCharging.numThresholdFeesCharged() > 0 || cachingFeePaid > 0L) {
			record = txnCtx.recordSoFar();
		}
		addNonThreshXQualifiers(record, qualifiers);

		int accountTtl = properties.getIntProperty("ledger.records.ttl");
		long accountRecordExpiry = txnCtx.consensusTime().getEpochSecond() + accountTtl;
		lastCreatedRecord = asExpirableRecord(record, accountRecordExpiry);
		log.debug("Last created record updated to: {}", record);
		addToEachAccount(qualifiers, lastCreatedRecord);

		/* --- NOTE ---
			In an upcoming release, we will store short-lived cache records in state
			to make duplicate classification deterministic in the presence of node restarts.
			This will be done in a fashion similar to:
			...
				int cacheTtl = properties.getIntProperty("cache.records.ttl");
				long cacheRecordExpiry = txnCtx.consensusTime().getEpochSecond() + cacheTtl;
				if (properties.getBooleanProperty("ledger.records.addCacheRecordToState")) {
					JTransactionRecord jRecord = asJRecord(record, cacheRecordExpiry);
					AccountID id = txnCtx.isPayerSigKnownActive()
							? txnCtx.accessor().getPayer()
							: properties.getAccountProperty("ledger.funding.account");
					addToAccountRecordCache(id, jRecord);
				}
			...
		*/

		if (isScopedRecordQueryable.test(txnCtx)) {
			recordCache.setPostConsensus(txnCtx.accessor().getTxnId(), lastCreatedRecord);
		} else {
			log.warn(
					"No queryable record will be available for consensus transaction {} (final status '{}')!",
					txnCtx.accessor().getSignedTxn4Log(),
					txnCtx.status());
		}
	}

	@Override
	public void purgeExpiredRecords() {
		long now = txnCtx.consensusTime().getEpochSecond();

		purgeLedgerRecords(now);
	}

	@Override
	public void reviewExistingRecords(long consensusTimeOfLastHandledTxn) {
		for (Map.Entry<MerkleEntityId, MerkleAccount> entry : accounts.entrySet()) {
			if (entry.getValue().expiryOfEarliestRecord() > -1L) {
				EarliestRecordExpiry ere = asEre(entry);
				expirations.offer(ere);
				accountsWithExpiringRecords.add(ere.getId());
			}
		}
	}

	private EarliestRecordExpiry asEre(Map.Entry<MerkleEntityId, MerkleAccount> entry) {
		MerkleEntityId key = entry.getKey();
		MerkleAccount account = entry.getValue();

		EarliestRecordExpiry ere = new EarliestRecordExpiry(
				account.expiryOfEarliestRecord(),
				AccountID.newBuilder()
						.setShardNum(key.getShard())
						.setRealmNum(key.getRealm())
						.setAccountNum(key.getNum())
						.build());
		return ere;
	}

	private boolean qualifiesForRecord(AccountAmount adjustment, long recordFee) {
		AccountID id = adjustment.getAccountID();
		if (ledger.isPendingCreation(id)) {
			return false;
		}
		long balance = ledger.getBalance(id);
		if (balance < recordFee) {
			return false;
		}

		long amount = adjustment.getAmount();
		return checkIfAmountUnderThreshold(amount, id);
	}

	private boolean checkIfAmountUnderThreshold(long amount, AccountID id) {
		if (amount < 0) {
			return -1 * amount > ledger.fundsSentRecordThreshold(id);
		} else {
			return amount > ledger.fundsReceivedRecordThreshold(id);
		}
	}

	private void payForThresholdRecords(Set<AccountID> ids) {
		ids.forEach(id -> feeCharging.chargeParticipant(id, THRESHOLD_RECORD_FEE));
	}

	private Set<AccountID> getThreshXQualifiers(long recordFee) {
		return ledger.netTransfersInTxn().getAccountAmountsList()
				.stream()
				.filter(aa -> qualifiesForRecord(aa, recordFee))
				.map(AccountAmount::getAccountID)
				.collect(toSet());
	}

	private void addToEachAccount(Set<AccountID> ids, ExpirableTxnRecord jRecord) {
		ids.forEach(id -> addToAccount(id, jRecord));
	}

	/* We can take for granted that records are added in monotonic
	increasing order of expiry, since account records expire a
	fixed distance from the ever-advancing consensus time. */
	private void addToAccount(AccountID id, ExpirableTxnRecord jRecord) {
		ledger.addRecord(id, jRecord);
		if (!accountsWithExpiringRecords.contains(id)) {
			expirations.offer(new EarliestRecordExpiry(jRecord.getExpiry(), id));
			accountsWithExpiringRecords.add(id);
		}
	}

	private ExpirableTxnRecord asExpirableRecord(TransactionRecord record, long expiry) {
		var expirableRecord = ExpirableTxnRecord.fromGprc(record);
		expirableRecord.setExpiry(expiry);
		return expirableRecord;
	}

	private boolean isCallableContract(AccountID id) {
		return Optional.ofNullable(accounts.get(MerkleEntityId.fromPojoAccountId(id)))
				.map(v -> v.isSmartContract() && !v.isDeleted())
				.orElse(false);
	}

	private long payForCaching(TransactionRecord record) {
			feeCharging.setFor(CACHE_RECORD, fees.computeCachingFee(record));
			feeCharging.chargePayerUpTo(CACHE_RECORD_FEE);
			return feeCharging.chargedToPayer(CACHE_RECORD);
	}

	private void addNonThreshXQualifiers(TransactionRecord record, Set<AccountID> qualifiers) {
		TransactionBody txn = txnCtx.accessor().getTxn();
		if (txn.hasContractCreateInstance()) {
			if (txnCtx.status() == SUCCESS) {
				qualifiers.add(EntityIdUtils.asAccount(record.getReceipt().getContractID()));
			}
		} else if (txn.hasContractCall()) {
			AccountID id = EntityIdUtils.asAccount(txn.getContractCall().getContractID());
			if (isCallableContract(id)) {
				qualifiers.add(id);
			}
		}
	}

	private void purgeLedgerRecords(long now) {
		Stopwatch watch = Stopwatch.createStarted();
		HederaLedger.LedgerTxnEvictionStats.INSTANCE.reset();

		while (!expirations.isEmpty() && (expirations.peek().getEarliestExpiry() < now)) {
			EarliestRecordExpiry ere = expirations.poll();
			AccountID id = ere.getId();

			long newEarliestExpiry = ledger.purgeExpiredRecords(id, now);
			if (newEarliestExpiry != -1L) {
				EarliestRecordExpiry newEre = new EarliestRecordExpiry(newEarliestExpiry, id);
				expirations.offer(newEre);
				log.debug("Replaced {} with {} @ {}", ere, newEre, txnCtx.consensusTime().getEpochSecond());
			} else {
				accountsWithExpiringRecords.remove(id);
			}
		}

		log.debug("Purged {} records from {} accounts in {}ms",
				HederaLedger.LedgerTxnEvictionStats.INSTANCE.recordsPurged(),
				HederaLedger.LedgerTxnEvictionStats.INSTANCE.accountsTouched(),
				watch.elapsed(MILLISECONDS));
	}
}
