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

import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Instant;

import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.randomUtf8ByteString;

public class FileCreateTxnFactory extends CreateTxnFactory<FileCreateTxnFactory> {

	private static KeyList waclKeys;
	{
		waclKeys = KeyList.newBuilder().addKeys(0, KeyFactory.getKey()).build();
	}

	private FileCreateTxnFactory() {}
	public static FileCreateTxnFactory newSignedFileCreate() {
		return new FileCreateTxnFactory();
	}

	@Override
	protected FileCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		Timestamp.Builder expiry = Timestamp.newBuilder()
				.setSeconds(Instant.now().getEpochSecond() + 1000000)
				.setNanos(0);

		FileCreateTransactionBody.Builder op = FileCreateTransactionBody.newBuilder()
				.setContents(randomUtf8ByteString(256))
				.setExpirationTime(expiry)
				.setKeys(waclKeys);
		txn.setFileCreate(op);
	}

	public FileCreateTxnFactory waclKeys(Key waclKey) {
		this.waclKeys = KeyList.newBuilder().setKeys(0, waclKey).build();
		return this;
	}
}
