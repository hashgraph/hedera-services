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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.AccountID;

import java.time.Instant;

public class EntityAutoRenewal {
	private static final com.hederahashgraph.api.proto.java.Transaction EMPTY =
			com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance();

	private ServicesContext ctx;

	public EntityAutoRenewal(ServicesContext ctx) {
		this.ctx = ctx;
	}

	public void execute(Instant consensusTime) {
		AccountID accountID = AccountID.newBuilder()
				.setShardNum(0L)
				.setRealmNum(0L)
				.setAccountNum(1001L)
				.build();
		ctx.backingAccounts().remove(accountID);
		Instant actionTime = consensusTime.plusNanos(1L);
		var record = EntityRemovalRecord.generatedFor(accountID, actionTime, accountID);
		var recordStreamObject = new RecordStreamObject(record, EMPTY, actionTime);
		ctx.updateRecordRunningHash(recordStreamObject.getRunningHash());
		ctx.recordStreamManager().addRecordStreamObject(recordStreamObject);
	}
}
