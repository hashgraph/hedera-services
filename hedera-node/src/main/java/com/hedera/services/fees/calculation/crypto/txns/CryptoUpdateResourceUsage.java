package com.hedera.services.fees.calculation.crypto.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.state.merkle.MerkleEntityId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.fees.calculation.FeeCalcUtils.lookupAccountExpiry;
import static com.hedera.services.legacy.core.jproto.JKey.mapJKey;

public class CryptoUpdateResourceUsage implements TxnResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(CryptoUpdateResourceUsage.class);

	private final CryptoFeeBuilder usageEstimator;

	public CryptoUpdateResourceUsage(CryptoFeeBuilder usageEstimator) {
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasCryptoUpdateAccount();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
		try {
			MerkleEntityId id = MerkleEntityId.fromAccountId(txn.getCryptoUpdateAccount().getAccountIDToUpdate());
			Timestamp expiry = lookupAccountExpiry(id, view.accounts());
			Key key = mapJKey(view.accounts().get(id).getKey());
			return usageEstimator.getCryptoUpdateTxFeeMatrices(txn, sigUsage, expiry, key);
		} catch (Exception ignore) {
			log.warn("Unable to deduce CryptoUpdate usage for {}, using defaults", txn.getTransactionID());
			return FeeData.getDefaultInstance();
		}
	}
}
