package com.hedera.services.usage.token;

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

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.UsageProperties;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public abstract class TokenUsage<T extends TokenUsage<T>> {
	protected static final int AMOUNT_REPR_BYTES = 8;

	static UsageProperties usageProperties = USAGE_PROPERTIES;
	static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;
	static EstimatorFactory estimatorFactory = TxnUsageEstimator::new;

	protected final TransactionBody tokenOp;
	protected final TxnUsageEstimator usageEstimator;

	abstract T self();

	protected TokenUsage(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
		this.tokenOp = tokenOp;
		this.usageEstimator = usageEstimator;
	}

	protected void addAmountBpt() {
		usageEstimator.addBpt(AMOUNT_REPR_BYTES);
	}

	protected void addAccountBpt() {
		usageEstimator.addBpt(BASIC_ENTITY_ID_SIZE);
	}

	protected void addRefBpt(TokenRef ref) {
		usageEstimator.addBpt(TokenUsageUtils.refBpt(ref));
	}

	protected void addNetworkRecordRb(long rb) {
		usageEstimator.addNetworkRbs(rb * usageProperties.legacyReceiptStorageSecs());
	}

	protected void addRecordRb(long rb) {
		usageEstimator.addRbs(rb * usageProperties.legacyReceiptStorageSecs());
	}

	protected void addTransfersRecordRb(int numTokens, int numTransfers) {
		addRecordRb(tokenEntitySizes.bytesUsedToRecordTransfers(numTokens, numTransfers));
	}

	public T novelRelsLasting(int n, long secs) {
		usageEstimator.addRbs(n * tokenEntitySizes.bytesUsedPerAccountRelationship() * secs);
		return self();
	}
}
