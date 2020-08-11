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

	public static final Boolean MARKER = Boolean.TRUE;

	private EntityCreator creator;
	private Cache<TransactionID, Boolean> timedReceiptCache;
	private Map<TransactionID, TxnIdRecentHistory> histories;

	public RecordCache(
			EntityCreator creator,
			Cache<TransactionID, Boolean> timedReceiptCache,
			Map<TransactionID, TxnIdRecentHistory> histories
	) {
		this.creator = creator;
		this.histories = histories;
		this.timedReceiptCache = timedReceiptCache;
	}

	public void addPreConsensus(TransactionID txnId) {
		timedReceiptCache.put(txnId, Boolean.TRUE);
	}

	public void setPostConsensus(
			TransactionID txnId,
			ResponseCodeEnum status,
			ExpirableTxnRecord record
	) {
		var recentHistory = histories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory());
		recentHistory.observe(record, status);
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
				consensusTimestamp.getEpochSecond(),
				submittingMember);
		var recentHistory = histories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory());
		recentHistory.observe(record, FAIL_INVALID);
	}

	public boolean isReceiptPresent(TransactionID txnId) {
		return histories.containsKey(txnId) ? true : timedReceiptCache.getIfPresent(txnId) == MARKER;
	}

	public TransactionReceipt getReceipt(TransactionID txnId) {
		var recentHistory = histories.get(txnId);
		return recentHistory != null
				? receiptFrom(recentHistory)
				: (timedReceiptCache.getIfPresent(txnId) == MARKER ? UNKNOWN_RECEIPT : null);
	}

	private TransactionReceipt receiptFrom(TxnIdRecentHistory recentHistory) {
		return Optional.ofNullable(recentHistory.legacyQueryableRecord())
				.map(ExpirableTxnRecord::getReceipt)
				.map(TxnReceipt::toGrpc)
				.orElse(UNKNOWN_RECEIPT);
	}

	public TransactionRecord getRecord(TransactionID txnId) {
		var history = histories.get(txnId);
		if (history != null) {
			return Optional.ofNullable(history.legacyQueryableRecord())
					.map(ExpirableTxnRecord::asGrpc)
					.orElse(null);
		}
		return null;
	}
}
