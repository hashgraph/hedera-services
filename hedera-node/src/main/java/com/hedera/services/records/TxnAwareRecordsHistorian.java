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
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 */
@Singleton
public class TxnAwareRecordsHistorian implements AccountRecordsHistorian {
	private ExpirableTxnRecord lastExpirableRecord;

	private EntityCreator creator;

	private final RecordCache recordCache;
	private final ExpiryManager expiries;
	private final TransactionContext txnCtx;

	@Inject
	public TxnAwareRecordsHistorian(RecordCache recordCache, TransactionContext txnCtx, ExpiryManager expiries) {
		this.expiries = expiries;
		this.txnCtx = txnCtx;
		this.recordCache = recordCache;
	}

	@Override
	public ExpirableTxnRecord lastCreatedTopLevelRecord() {
		return lastExpirableRecord;
	}

	@Override
	public void setCreator(EntityCreator creator) {
		this.creator = creator;
	}

	@Override
	public void finalizeExpirableTransactionRecords() {
		lastExpirableRecord = txnCtx.recordSoFar();
	}

	@Override
	public void saveExpirableTransactionRecords() {
		long now = txnCtx.consensusTime().getEpochSecond();
		long submittingMember = txnCtx.submittingSwirldsMember();
		var accessor = txnCtx.accessor();
		var payerRecord = creator.saveExpiringRecord(
				txnCtx.effectivePayer(),
				lastExpirableRecord,
				now,
				submittingMember);
		recordCache.setPostConsensus(
				accessor.getTxnId(),
				ResponseCodeEnum.valueOf(lastExpirableRecord.getReceipt().getStatus()),
				payerRecord);
	}

	@Override
	public void noteNewExpirationEvents() {
		for (var expiringEntity : txnCtx.expiringEntities()) {
			expiries.trackExpirationEvent(
					Pair.of(expiringEntity.id().num(), expiringEntity.consumer()),
					expiringEntity.expiry());
		}
	}

	@Override
	public boolean hasChildRecords() {
		return false;
	}

	@Override
	public List<RecordStreamObject> getChildRecords() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public int nextChildRecordSourceId() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void trackChildRecord(final int sourceId, final Pair<ExpirableTxnRecord.Builder, Transaction> recordSoFar) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void revertChildRecordsFromSource(final int sourceId) {
		throw new AssertionError("Not implemented");
	}
}
