package com.hedera.services.state.expiry;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

import java.time.Instant;

public enum NoopExpiringCreations implements EntityCreator {
	NOOP_EXPIRING_CREATIONS;

	@Override
	public void setRecordCache(RecordCache recordCache) {
		/* No-op */
	}

	@Override
	public ExpirableTxnRecord saveExpiringRecord(
			AccountID id,
			ExpirableTxnRecord expiringRecord,
			long now,
			long submittingMember
	) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExpirableTxnRecord.Builder buildExpiringRecord(
			long otherNonThresholdFees,
			byte[] hash,
			TxnAccessor accessor,
			Timestamp consensusTimestamp,
			TxnReceipt receipt
	) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExpirableTxnRecord.Builder buildFailedExpiringRecord(TxnAccessor accessor, Instant consensusTimestamp) {
		throw new UnsupportedOperationException();
	}
}
