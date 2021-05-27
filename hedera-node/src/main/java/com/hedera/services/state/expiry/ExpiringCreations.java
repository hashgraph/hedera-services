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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

public class ExpiringCreations implements EntityCreator {
	private RecordCache recordCache;

	private final ExpiryManager expiries;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public ExpiringCreations(
			ExpiryManager expiries,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.expiries = expiries;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void setRecordCache(RecordCache recordCache) {
		this.recordCache = recordCache;
	}

	@Override
	public ExpirableTxnRecord saveExpiringRecord(
			AccountID payer,
			ExpirableTxnRecord record,
			long now,
			long submittingMember
	) {
		long expiry = now + dynamicProperties.cacheRecordsTtl();
		record.setExpiry(expiry);
		record.setSubmittingMember(submittingMember);

		if (dynamicProperties.shouldKeepRecordsInState()) {
			final var key = MerkleEntityId.fromAccountId(payer);
			addToState(key, record);
			expiries.trackRecordInState(payer, record.getExpiry());
		} else {
			recordCache.trackForExpiry(record);
		}

		return record;
	}

	private void addToState(MerkleEntityId key, ExpirableTxnRecord record) {
		final var currentAccounts = accounts.get();
		final var mutableAccount = currentAccounts.getForModify(key);
		mutableAccount.records().offer(record);
		currentAccounts.replace(key, mutableAccount);
	}
}
