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

import com.google.common.cache.Cache;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.sha384HashOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class RecordCache {
	static final TransactionReceipt UNKNOWN_RECEIPT = TransactionReceipt.newBuilder()
			.setStatus(UNKNOWN)
			.build();

	private static final Boolean MARKER = Boolean.TRUE;

	private EntityCreator creator;
	private Cache<TransactionID, Boolean> receiptCache;
	private Map<TransactionID, TxnIdRecentHistory> histories;

	private Cache<TransactionID, Optional<TransactionRecord>> delegate;

	public RecordCache(
			EntityCreator creator,
			Cache<TransactionID, Boolean> receiptCache,
			Map<TransactionID, TxnIdRecentHistory> histories
	) {
		this.creator = creator;
		this.histories = histories;
		this.receiptCache = receiptCache;
	}

	public RecordCache(Cache<TransactionID, Optional<TransactionRecord>> delegate) {
		this.delegate = delegate;
	}

	public void addPreConsensus(TransactionID txnId) {
		receiptCache.put(txnId, Boolean.TRUE);
	}

	public void setPostConsensus(
			TransactionID txnId,
			ResponseCodeEnum status,
			ExpirableTxnRecord record,
			long submittingMember
	) {
		var recentHistory = histories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory());
		recentHistory.observe(record, status, submittingMember);
	}

	public void setFailInvalid(
			AccountID effectivePayer,
			PlatformTxnAccessor accessor,
			Instant consensusTimestamp,
			long submittingMember
	) {
		var txnId = accessor.getTxnId();
		var grpc = TransactionRecord.newBuilder()
				.setTransactionID(txnId)
				.setReceipt(TransactionReceipt.newBuilder().setStatus(FAIL_INVALID))
				.setMemo(accessor.getTxn().getMemo())
				.setTransactionHash(sha384HashOf(accessor))
				.setConsensusTimestamp(asTimestamp(consensusTimestamp))
				.build();
		var record = creator.createExpiringPayerRecord(
				effectivePayer,
				grpc,
				consensusTimestamp.getEpochSecond());
		var recentHistory = histories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory());
		recentHistory.observe(record, FAIL_INVALID, submittingMember);
	}

	public boolean isReceiptPresent(TransactionID txnId) {
		return Optional.ofNullable(delegate.getIfPresent(txnId)).map(ignore -> true).orElse(false);
	}

	public boolean isRecordPresent(TransactionID txnId) {
		return Optional.ofNullable(delegate.getIfPresent(txnId)).orElse(Optional.empty()).isPresent();
	}

	public TransactionReceipt getReceipt(TransactionID txnId) {
		var recentHistory = histories.get(txnId);
		return recentHistory != null
				? receiptFrom(recentHistory)
				: (receiptCache.getIfPresent(txnId) == MARKER ? UNKNOWN_RECEIPT : null);
	}

	private TransactionReceipt receiptFrom(TxnIdRecentHistory recentHistory) {
		return Optional.ofNullable(recentHistory.legacyQueryableRecord())
				.map(ExpirableTxnRecord::getReceipt)
				.map(TxnReceipt::toGrpc)
				.orElse(null);
	}

	public TransactionRecord getRecord(TransactionID txnId) {
		return Optional.ofNullable(histories.get(txnId))
				.map(TxnIdRecentHistory::legacyQueryableRecord)
				.map(ExpirableTxnRecord::asGrpc)
				.orElse(null);
	}
}
