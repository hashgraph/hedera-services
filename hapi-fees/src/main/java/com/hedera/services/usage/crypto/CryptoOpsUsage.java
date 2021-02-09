package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.TxnUsage.keySizeIfPresent;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class CryptoOpsUsage {
	static EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;

	public FeeData cryptoCreateUsage(TransactionBody cryptoCreation, SigUsage sigUsage) {
		var op = cryptoCreation.getCryptoCreateAccount();

		int customBytes = 0;
		customBytes += op.getMemoBytes().size();
		customBytes += keySizeIfPresent(op, CryptoCreateTransactionBody::hasKey, CryptoCreateTransactionBody::getKey);
		if (op.hasProxyAccountID()) {
			customBytes += BASIC_ENTITY_ID_SIZE;
		}

		var lifetime = op.getAutoRenewPeriod().getSeconds();

		var estimate = txnEstimateFactory.get(sigUsage, cryptoCreation, ESTIMATOR_UTILS);
		/* Variable bytes plus a long for auto-renew period */
		estimate.addBpt(customBytes + LONG_SIZE);
		estimate.addRbs((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + customBytes) * lifetime);
		estimate.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

		return estimate.get();
	}
}
