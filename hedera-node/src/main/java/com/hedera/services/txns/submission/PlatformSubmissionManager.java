package com.hedera.services.txns.submission;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.records.RecordCache;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.Platform;
import com.swirlds.common.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class PlatformSubmissionManager {
	private static final Logger log = LogManager.getLogger(TxnHandlerSubmissionFlow.class);

	private final Platform platform;
	private final RecordCache recordCache;
	private final HederaNodeStats stats;

	public PlatformSubmissionManager(
			Platform platform,
			RecordCache recordCache,
			HederaNodeStats stats
	) {
		this.platform = platform;
		this.recordCache = recordCache;
		this.stats = stats;
	}

	public ResponseCodeEnum trySubmission(SignedTxnAccessor accessor) {
		accessor = effective(accessor);

		var success = (accessor != null) && platform.createTransaction(new Transaction(accessor.getSignedTxnBytes()));
		if (success) {
			recordCache.addPreConsensus(accessor.getTxnId());
			return OK;
		} else {
			stats.platformTxnNotCreated();
			return PLATFORM_TRANSACTION_NOT_CREATED;
		}
	}

	private SignedTxnAccessor effective(SignedTxnAccessor accessor) {
		var txn = accessor.getTxn();
		if (txn.hasUncheckedSubmit()) {
			try {
				return new SignedTxnAccessor(txn.getUncheckedSubmit().getTransactionBytes().toByteArray());
			} catch (InvalidProtocolBufferException e) {
				log.warn("Transaction bytes from UncheckedSubmit not a valid gRPC transaction!", e);
				return null;
			}
		} else {
			return accessor;
		}
	}
}
