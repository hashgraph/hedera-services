package com.hedera.services.state.expiry;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public class ExpiringCreations implements EntityCreator {
	private RecordCache recordCache;
	private HederaLedger ledger;
	private final ExpiryManager expiries;
	private final GlobalDynamicProperties dynamicProperties;

	public ExpiringCreations(
			ExpiryManager expiries,
			GlobalDynamicProperties dynamicProperties
	) {
		this.expiries = expiries;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void setRecordCache(RecordCache recordCache) {
		this.recordCache = recordCache;
	}

	@Override
	public void setLedger(HederaLedger ledger) {
		this.ledger = ledger;
	}

	@Override
	public ExpirableTxnRecord createExpiringRecord(
			AccountID id,
			TransactionRecord record,
			long now,
			long submittingMember
	) {
		var expiringRecord = ExpirableTxnRecord.fromGprc(record);

		long expiry = now + dynamicProperties.cacheRecordsTtl();
		expiringRecord.setExpiry(expiry);
		expiringRecord.setSubmittingMember(submittingMember);

		manageRecord(id, expiringRecord);

		return expiringRecord;
	}

	private void manageRecord(AccountID owner, ExpirableTxnRecord record) {
		if (dynamicProperties.shouldKeepRecordsInState()) {
			ledger.addRecord(owner, record);
			expiries.trackRecord(owner, record.getExpiry());
		} else {
			recordCache.trackForExpiry(record);
		}
	}

}
