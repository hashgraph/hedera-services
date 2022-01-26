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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.utils.MiscUtils.nonNegativeNanosOffset;

/**
 * Provides a {@link AccountRecordsHistorian} using the natural collaborators.
 */
@Singleton
public class TxnAwareRecordsHistorian implements AccountRecordsHistorian {
	public static final int DEFAULT_SOURCE_ID = 0;

	private final RecordCache recordCache;
	private final ExpiryManager expiries;
	private final TransactionContext txnCtx;
	private final List<RecordStreamObject> precedingChildStreamObjs = new ArrayList<>();
	private final List<RecordStreamObject> followingChildStreamObjs = new ArrayList<>();
	private final List<InProgressChildRecord> precedingChildRecords = new ArrayList<>();
	private final List<InProgressChildRecord> followingChildRecords = new ArrayList<>();

	private int nextNonce = USER_TRANSACTION_NONCE + 1;
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
	public Instant nextFollowingChildConsensusTime() {
		return txnCtx.consensusTime().plusNanos(1L + followingChildRecords.size());
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
		final var topLevel = txnCtx.recordSoFar();
		final var numChildren = (short) (precedingChildRecords.size() + followingChildRecords.size());

		finalizeChildRecords(consensusNow, topLevel);
		topLevelRecord = topLevel.setNumChildRecords(numChildren).build();

		final var effPayer = txnCtx.effectivePayer();
		final var accessor = txnCtx.accessor();
		final var submittingMember = txnCtx.submittingSwirldsMember();

		save(precedingChildStreamObjs, effPayer, submittingMember);
		save(topLevelRecord, effPayer, accessor.getTxnId(), submittingMember, consensusNow.getEpochSecond());
		save(followingChildStreamObjs, effPayer, submittingMember);
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
	public boolean hasFollowingChildRecords() {
		return !followingChildStreamObjs.isEmpty();
	}

	@Override
	public boolean hasPrecedingChildRecords() {
		return !precedingChildStreamObjs.isEmpty();
	}

	@Override
	public List<RecordStreamObject> getFollowingChildRecords() {
		return followingChildStreamObjs;
	}

	@Override
	public List<RecordStreamObject> getPrecedingChildRecords() {
		return precedingChildStreamObjs;
	}

	@Override
	public int nextChildRecordSourceId() {
		return nextSourceId++;
	}

	@Override
	public void trackFollowingChildRecord(
			final int sourceId,
			final TransactionBody.Builder syntheticBody,
			final ExpirableTxnRecord.Builder recordSoFar
	) {
		final var inProgress = new InProgressChildRecord(sourceId, syntheticBody, recordSoFar);
		followingChildRecords.add(inProgress);
	}

	@Override
	public void trackPrecedingChildRecord(
			final int sourceId,
			final TransactionBody.Builder syntheticTxn,
			final ExpirableTxnRecord.Builder recordSoFar
	) {
		final var inProgress = new InProgressChildRecord(sourceId, syntheticTxn, recordSoFar);
		precedingChildRecords.add(inProgress);
	}

	@Override
	public void revertChildRecordsFromSource(final int sourceId) {
		revert(sourceId, precedingChildRecords);
		revert(sourceId, followingChildRecords);
	}

	@Override
	public void clearHistory() {
		precedingChildRecords.clear();
		precedingChildStreamObjs.clear();
		followingChildRecords.clear();
		followingChildStreamObjs.clear();

		nextNonce = USER_TRANSACTION_NONCE + 1;
		nextSourceId = 1;
	}

	private void revert(final int sourceId, final List<InProgressChildRecord> childRecords) {
		for (final var inProgress : childRecords) {
			if (inProgress.sourceId() == sourceId) {
				inProgress.recordBuilder().revert();
			}
		}
	}

	private void finalizeChildRecords(final Instant consensusNow, final ExpirableTxnRecord.Builder topLevel) {
		finalizeChildrenVerbosely(-1, precedingChildRecords, precedingChildStreamObjs, consensusNow, topLevel);
		finalizeChildrenVerbosely(+1, followingChildRecords, followingChildStreamObjs, consensusNow, topLevel);
	}

	private void finalizeChildrenVerbosely(
			final int sigNum,
			final List<InProgressChildRecord> childRecords,
			final List<RecordStreamObject> recordObjs,
			final Instant consensusNow,
			final ExpirableTxnRecord.Builder topLevel
	) {
		final var parentId = topLevel.getTxnId();
		for (int i = 0, n = childRecords.size(); i < n; i++) {
			final var inProgress = childRecords.get(i);
			final var child = inProgress.recordBuilder();
			topLevel.excludeHbarChangesFrom(child);

			child.setTxnId(parentId.withNonce(nextNonce++));
			final var childConsTime = nonNegativeNanosOffset(consensusNow, sigNum * (i + 1));
			child.setConsensusTime(RichInstant.fromJava(childConsTime));
			/* Mirror node team prefers we only set a parent consensus time for records that FOLLOW
			 * the top-level transaction. This might change for future use cases. */
			if (sigNum > 0) {
				child.setParentConsensusTime(consensusNow);
			}

			final var synthTxn = synthFrom(inProgress.syntheticBody(), child);
			final var synthHash = noThrowSha384HashOf(synthTxn.getSignedTransactionBytes().toByteArray());
			child.setTxnHash(synthHash);
			recordObjs.add(new RecordStreamObject(child.build(), synthTxn, childConsTime));
		}
	}

	private Transaction synthFrom(final TransactionBody.Builder txnBody, final ExpirableTxnRecord.Builder inProgress) {
		return synthFromBody(txnBody.setTransactionID(inProgress.getTxnId().toGrpc()).build());
	}

	private Transaction synthFromBody(final TransactionBody txnBody) {
		final var signedTxn = SignedTransaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build();
		return Transaction.newBuilder()
				.setSignedTransactionBytes(signedTxn.toByteString())
				.build();
	}

	private void save(
			final List<RecordStreamObject> baseRecords,
			final AccountID effPayer,
			final long submittingMember
	) {
		for (final var baseRecord : baseRecords) {
			final var childRecord = baseRecord.getExpirableTransactionRecord();
			save(
					childRecord,
					effPayer,
					childRecord.getTxnId().toGrpc(),
					submittingMember,
					childRecord.getConsensusSecond());
		}
	}

	private void save(
			final ExpirableTxnRecord baseRecord,
			final AccountID effPayer,
			final TransactionID txnId,
			final long submittingMember,
			final long consensusSecond
	) {
		final var expiringRecord = creator.saveExpiringRecord(
				effPayer, baseRecord, consensusSecond, submittingMember);
		recordCache.setPostConsensus(txnId, baseRecord.getEnumStatus(), expiringRecord);
	}
}
