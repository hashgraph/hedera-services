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
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.core.jproto.JTransactionReceipt;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;

import java.time.Instant;
import java.util.Optional;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.sha384HashOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

/**
 * Uses a {@link Cache} to store {@link JTransactionRecord} instances by their
 * {@link TransactionID}. (Somewhat more precisely, an {@link Optional} is stored
 * for each id, and if this optional is empty, it indicates the transaction with
 * that id has been submitted to the platform, but not yet incorporated to state.)
 *
 * @author Michael Tinker
 */
public class RecordCache {
	static final TransactionReceipt UNKNOWN_RECEIPT = TransactionReceipt.newBuilder()
			.setStatus(UNKNOWN)
			.build();

	private final Cache<TransactionID, Optional<JTransactionRecord>> delegate;

	public RecordCache(Cache<TransactionID, Optional<JTransactionRecord>> delegate) {
		this.delegate = delegate;
	}

	public void addPreConsensus(TransactionID txnId) {
		delegate.put(txnId, Optional.empty());
	}

	public void setPostConsensus(TransactionID txnId, JTransactionRecord record) {
		delegate.put(txnId, Optional.of(record));
	}

	public void setFailInvalid(PlatformTxnAccessor accessor, Instant consensusTimestamp) {
		TransactionID txnId = accessor.getTxnId();
		TransactionRecord.Builder record = TransactionRecord.newBuilder()
				.setTransactionID(txnId)
				.setReceipt(TransactionReceipt.newBuilder().setStatus(FAIL_INVALID))
				.setMemo(accessor.getTxn().getMemo())
				.setTransactionHash(sha384HashOf(accessor))
				.setConsensusTimestamp(asTimestamp(consensusTimestamp));
		delegate.put(txnId, Optional.of(JTransactionRecord.convert(record.build())));
	}

	public boolean isReceiptPresent(TransactionID txnId) {
		return Optional.ofNullable(delegate.getIfPresent(txnId)).map(ignore -> true).orElse(false);
	}

	public boolean isRecordPresent(TransactionID txnId) {
		return Optional.ofNullable(delegate.getIfPresent(txnId)).orElse(Optional.empty()).isPresent();
	}

	public TransactionReceipt getReceipt(TransactionID txnId) {
		Optional<JTransactionRecord> record = delegate.getIfPresent(txnId);
		if (record == null) {
			return null;
		}
		return record.map(r -> JTransactionReceipt.convert(r.getTxReceipt())).orElse(UNKNOWN_RECEIPT);
	}

	public TransactionRecord getRecord(TransactionID txnId) {
		Optional<JTransactionRecord> record = delegate.getIfPresent(txnId);
		if ( record != null && record.isPresent()) {
			return record.map(JTransactionRecord::convert).get();
		}
		return null;
	}
}
