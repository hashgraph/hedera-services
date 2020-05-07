package com.hedera.test.factories.txns;

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

import com.swirlds.common.Transaction;

public class PlatformTxnFactory {
	public static Transaction from(com.hederahashgraph.api.proto.java.Transaction signedTxn) {
		return new Transaction(signedTxn.toByteArray());
	}

	public static TransactionWithClearFlag withClearFlag(Transaction txn) {
		return new TransactionWithClearFlag(txn.getContents());
	}

	public static class TransactionWithClearFlag extends Transaction {
		private boolean hasClearBeenCalled = false;

		public TransactionWithClearFlag(byte[] contents) {
			super(contents);
		}

		@Override
		public void clear() {
			hasClearBeenCalled = true;
			super.clear();
		}

		public boolean hasClearBeenCalled() {
			return hasClearBeenCalled;
		}
	}
}
