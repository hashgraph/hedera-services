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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public enum NoopExpiringCreations implements EntityCreator {
	NOOP_EXPIRING_CREATIONS;

	@Override
	public void setLedger(HederaLedger ledger) { }

	@Override
	public void setRecordCache(RecordCache recordCache) { }

	@Override
	public ExpirableTxnRecord createExpiringPayerRecord(
			AccountID id,
			TransactionRecord record,
			long now,
			long submittingMember
	) {
		throw new UnsupportedOperationException();
	}
}
