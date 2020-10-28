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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 *
 * @author Michael Tinker
 */
public class TxnAwareRecordsHistorian implements AccountRecordsHistorian {
	private HederaLedger ledger;
	private TransactionRecord lastCreatedRecord;

	private EntityCreator creator;

	private final RecordCache recordCache;
	private final ExpiryManager expiries;
	private final TransactionContext txnCtx;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public TxnAwareRecordsHistorian(
			RecordCache recordCache,
			TransactionContext txnCtx,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			ExpiryManager expiries
	) {
		this.expiries = expiries;
		this.txnCtx = txnCtx;
		this.accounts = accounts;
		this.recordCache = recordCache;
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
	}

	@Override
	public void addNewRecords() {
		lastCreatedRecord = txnCtx.recordSoFar();

		long now = txnCtx.consensusTime().getEpochSecond();
		long submittingMember = txnCtx.submittingSwirldsMember();

		var accessor = txnCtx.accessor();
		var payerRecord = creator.createExpiringRecord(
				txnCtx.effectivePayer(),
				lastCreatedRecord,
				now,
				submittingMember);
		recordCache.setPostConsensus(
				accessor.getTxnId(),
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
}
