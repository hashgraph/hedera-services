package com.hedera.services.state;

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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.submerkle.AssessedCustomFee;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.time.Instant;
import java.util.List;

public interface EntityCreator {
	/**
	 * setter for {@link RecordCache} in {@link EntityCreator}
	 *
	 * @param recordCache
	 * 		record cache
	 */
	void setRecordCache(RecordCache recordCache);

	/**
	 * Sets needed properties like expiry and submitting member to {@link ExpirableTxnRecord} and adds record to state
	 * based on {@code GlobalDynamicProperties.cacheRecordsTtl}. If not it is added to be tracked for Expiry to {@link
	 * RecordCache}
	 *
	 * @param id
	 * 		account id
	 * @param expiringRecord
	 * 		expirable transaction record
	 * @param consensusTime
	 * 		consensus timestamp
	 * @param submittingMember
	 * 		submitting member
	 * @return the {@link ExpirableTxnRecord} after setting needed properties
	 */
	ExpirableTxnRecord saveExpiringRecord(
			AccountID id,
			ExpirableTxnRecord expiringRecord,
			long consensusTime,
			long submittingMember);

	/**
	 * Build {@link ExpirableTxnRecord.Builder} when the record is finalized before committing
	 * the active transaction
	 *
	 * @param otherNonThresholdFees
	 * 		part of fees
	 * @param hash
	 * 		transaction hash
	 * @param accessor
	 * 		transaction accessor
	 * @param consensusTime
	 * 		consensus time
	 * @param receipt
	 * 		transaction receipt
	 * @param explicitTokenTransfers
	 * 		explicit list of token transfers
	 * @param ctx
	 * 		services context
	 * @param ctx
	 * 		custom fees charged
	 * @return a {@link ExpirableTxnRecord.Builder} for the finalized record
	 */
	ExpirableTxnRecord.Builder buildExpiringRecord(
			long otherNonThresholdFees,
			byte[] hash,
			TxnAccessor accessor,
			Instant consensusTime,
			TxnReceipt receipt,
			List<TokenTransferList> explicitTokenTransfers,
			ServicesContext ctx,
			List<AssessedCustomFee> customFeesCharged);

	/**
	 * Build a {@link ExpirableTxnRecord.Builder} for a transaction failed to commit
	 *
	 * @param accessor
	 * 		transaction accessor
	 * @param consensusTimestamp
	 * 		consensus timestamp
	 * @return a {@link ExpirableTxnRecord.Builder} for a transaction failed to commit
	 */
	ExpirableTxnRecord.Builder buildFailedExpiringRecord(TxnAccessor accessor, Instant consensusTimestamp);
}
