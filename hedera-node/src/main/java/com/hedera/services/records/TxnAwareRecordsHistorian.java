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
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 */
@Singleton
public class TxnAwareRecordsHistorian implements AccountRecordsHistorian {
	private static class InProgressChildRecord {
		private final int sourceId;
		private final Transaction syntheticTxn;
		private final ExpirableTxnRecord.Builder recordBuilder;

		public InProgressChildRecord(
				final int sourceId,
				final Transaction syntheticTxn,
				final ExpirableTxnRecord.Builder recordBuilder
		) {
			this.sourceId = sourceId;
			this.syntheticTxn = syntheticTxn;
			this.recordBuilder = recordBuilder;
		}
	}

	private final RecordCache recordCache;
	private final ExpiryManager expiries;
	private final TransactionContext txnCtx;
	private final List<RecordStreamObject> childStreamObjs = new ArrayList<>();
	private final List<InProgressChildRecord> childRecords = new ArrayList<>();

	private int nextSourceId = 1;
	private EntityCreator creator;

	private ExpirableTxnRecord topLevelRecord;

	@Inject
	public TxnAwareRecordsHistorian(RecordCache recordCache, TransactionContext txnCtx, ExpiryManager expiries) {
		this.expiries = expiries;
		this.txnCtx = txnCtx;
		this.recordCache = recordCache;
	}

	@Override
	public ExpirableTxnRecord lastCreatedTopLevelRecord() {
		return topLevelRecord;
	}

	@Override
	public void setCreator(EntityCreator creator) {
		this.creator = creator;
	}

	@Override
	public void saveExpirableTransactionRecords() {
		final var consensusNow = txnCtx.consensusTime();
		final var secondNow = consensusNow.getEpochSecond();

		final var submittingMember = txnCtx.submittingSwirldsMember();
		final var accessor = txnCtx.accessor();
		final var txnId = accessor.getTxnId();

		final var topLevel = txnCtx.recordSoFar();
		finalizeChildRecords(consensusNow, topLevel);

		final var effPayer = txnCtx.effectivePayer();
		topLevelRecord = topLevel.build();
		final var expiringTopLevelRecord = creator.saveExpiringRecord(
				effPayer, topLevelRecord, secondNow, submittingMember);
		recordCache.setPostConsensus(
				txnId, ResponseCodeEnum.valueOf(topLevelRecord.getReceipt().getStatus()), expiringTopLevelRecord);

		for (int i = 0, n = childRecords.size(); i < n; i++) {
			final var childRecord = childStreamObjs.get(i).getExpirableTransactionRecord();
			creator.saveExpiringRecord(effPayer, childRecord, secondNow, submittingMember);
		}
	}

	@Override
	public void noteNewExpirationEvents() {
		for (final var expiringEntity : txnCtx.expiringEntities()) {
			expiries.trackExpirationEvent(
					Pair.of(expiringEntity.id().num(), expiringEntity.consumer()),
					expiringEntity.expiry());
		}
	}

	@Override
	public boolean hasChildRecords() {
		return !childStreamObjs.isEmpty();
	}

	@Override
	public List<RecordStreamObject> getChildRecords() {
		return childStreamObjs;
	}

	@Override
	public int nextChildRecordSourceId() {
		return nextSourceId++;
	}

	@Override
	public void trackChildRecord(
			final int sourceId,
			final ExpirableTxnRecord.Builder recordSoFar,
			final Transaction syntheticTxn
	) {
		final var inProgress = new InProgressChildRecord(sourceId, syntheticTxn, recordSoFar);
		childRecords.add(inProgress);
	}

	@Override
	public void revertChildRecordsFromSource(final int sourceId) {
		for (final var inProgress : childRecords) {
			if (inProgress.sourceId == sourceId) {
				inProgress.recordBuilder.revert();
			}
		}
	}

	@Override
	public void clearHistory() {
		childRecords.clear();
		childStreamObjs.clear();

		nextSourceId = 1;
	}

	private void finalizeChildRecords(final Instant consensusNow, final ExpirableTxnRecord.Builder topLevel) {
		for (int i = 0, n = childRecords.size(); i < n; i++) {
			final var inProgress = childRecords.get(i);
			final var child = inProgress.recordBuilder;
			topLevel.excludeHbarChangesFrom(child);
			final var childConsTime = consensusNow.plusNanos(i + 1L);
			child.setConsensusTime(RichInstant.fromJava(childConsTime));
			childStreamObjs.add(new RecordStreamObject(child.build(), inProgress.syntheticTxn, childConsTime));
		}
	}
}
