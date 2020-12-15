package com.hedera.services.usage.crypto;
/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.TxnUsage;
import com.hedera.services.usage.token.entities.TokenEntitySizes;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;

public abstract class CryptoTxnUsage<T extends CryptoTxnUsage<T>> extends TxnUsage {
	static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;

	abstract T self();

	protected CryptoTxnUsage(TransactionBody cryptoOp, TxnUsageEstimator usageEstimator) {
		super(cryptoOp, usageEstimator);
	}

	void addTokenTransfersRecordRb(int numTokens, int numTransfers) {
		addRecordRb(tokenEntitySizes.bytesUsedToRecordTokenTransfers(numTokens, numTransfers));
	}
}
