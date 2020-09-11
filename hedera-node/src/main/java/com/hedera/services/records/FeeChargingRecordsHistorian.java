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
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.ItemizableFeeCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.fees.TxnFeeType.CACHE_RECORD;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.CACHE_RECORD_FEE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.THRESHOLD_RECORD_FEE;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toSet;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 *
 * @author Michael Tinker
 */
public class FeeChargingRecordsHistorian implements AccountRecordsHistorian {
	private HederaLedger ledger;
	private TransactionRecord lastCreatedRecord;

	private EntityCreator creator;

	private final RecordCache recordCache;
	private final FeeCalculator fees;
	private final ExpiryManager expiries;
	private final TransactionContext txnCtx;
	private final ItemizableFeeCharging feeCharging;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public FeeChargingRecordsHistorian(
			RecordCache recordCache,
			FeeCalculator fees,
			TransactionContext txnCtx,
			ItemizableFeeCharging feeCharging,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			ExpiryManager expiries
	) {
		this.fees = fees;
		this.expiries = expiries;
		this.txnCtx = txnCtx;
		this.accounts = accounts;
		this.recordCache = recordCache;
		this.feeCharging = feeCharging;
	}

	@Override
	public Optional<TransactionRecord> lastCreatedRecord() {
		return Optional.ofNullable(lastCreatedRecord);
	}

	@Override
	public void setCreator(EntityCreator creator) {
		this.creator = creator;
	}

	@Override
	public void setLedger(HederaLedger ledger) {
		this.ledger = ledger;
		feeCharging.setLedger(ledger);
	}

	@Override
	public void addNewRecords() {
		var record = txnCtx.recordSoFar();

		long cachingFeePaid = payForCaching(record);
		if ( cachingFeePaid > 0L) {
			record = txnCtx.updatedRecordGiven(ledger.netTransfersInTxn());
		}

		lastCreatedRecord = record;

		long now = txnCtx.consensusTime().getEpochSecond();
		long submittingMember = txnCtx.submittingSwirldsMember();
		Set<AccountID> qualifiers = new HashSet<>();
		addNonThreshXQualifiers(record, qualifiers);
		if (!qualifiers.isEmpty()) {
			createHistorical(qualifiers, record, now, submittingMember);
		}

		var payerRecord = creator.createExpiringPayerRecord(
				txnCtx.effectivePayer(),
				lastCreatedRecord,
				now,
				submittingMember);
		recordCache.setPostConsensus(
				txnCtx.accessor().getTxnId(),
				lastCreatedRecord.getReceipt().getStatus(),
				payerRecord);
	}

	@Override
	public void purgeExpiredRecords() {
		expiries.purgeExpiredRecordsAt(txnCtx.consensusTime().getEpochSecond(), ledger);
	}

	@Override
	public void reviewExistingRecords() {
		expiries.resumeTrackingFrom(accounts.get());
	}

	private void createHistorical(
			Set<AccountID> ids,
			TransactionRecord record,
			long now,
			long submittingMember
	) {
		ids.forEach(id -> creator.createExpiringHistoricalRecord(id, record, now, submittingMember));
	}

	private boolean isCallableContract(AccountID id) {
		return Optional.ofNullable(accounts.get().get(fromAccountId(id)))
				.map(v -> v.isSmartContract() && !v.isDeleted())
				.orElse(false);
	}

	private long payForCaching(TransactionRecord record) {
		feeCharging.setFor(CACHE_RECORD, fees.computeCachingFee(record));
		if (txnCtx.isPayerSigKnownActive()) {
			feeCharging.chargePayerUpTo(CACHE_RECORD_FEE);
			return feeCharging.chargedToPayer(CACHE_RECORD);
		} else {
			feeCharging.chargeSubmittingNodeUpTo(CACHE_RECORD_FEE);
			return feeCharging.chargedToSubmittingNode(CACHE_RECORD);
		}
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
}
