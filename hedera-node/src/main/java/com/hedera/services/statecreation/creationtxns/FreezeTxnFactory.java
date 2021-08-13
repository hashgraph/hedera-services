package com.hedera.services.statecreation.creationtxns;

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

import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Instant;

public class FreezeTxnFactory extends CreateTxnFactory<FreezeTxnFactory> {
	private Timestamp.Builder freezeStartTime;

	private FreezeTxnFactory() {}
	public static FreezeTxnFactory newFreezeTxn() {
		return new FreezeTxnFactory();
	}

	@Override
	protected FreezeTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		FreezeTransactionBody.Builder op = FreezeTransactionBody.newBuilder()
				.setStartTime(freezeStartTime);
		txn.setFreeze(op);
	}

	public FreezeTxnFactory freezeStartAt(Instant freezeStartAt) {
		freezeStartTime = Timestamp.newBuilder()
				.setSeconds(freezeStartAt.getEpochSecond())
				.setNanos(0);
		return this;
	}
}
