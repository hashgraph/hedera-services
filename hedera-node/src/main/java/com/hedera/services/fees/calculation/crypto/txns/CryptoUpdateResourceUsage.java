package com.hedera.services.fees.calculation.crypto.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;

public class CryptoUpdateResourceUsage implements TxnResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(CryptoUpdateResourceUsage.class);

	private final CryptoOpsUsage cryptoOpsUsage;

	public CryptoUpdateResourceUsage(CryptoOpsUsage cryptoOpsUsage) {
		this.cryptoOpsUsage = cryptoOpsUsage;
	}

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasCryptoUpdateAccount();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
		var op = txn.getCryptoUpdateAccount();
		var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
		var info = view.infoForAccount(op.getAccountIDToUpdate());
		if (info.isPresent()) {
			var details = info.get();
			var ctx = ExtantCryptoContext.newBuilder()
					.setCurrentKey(details.getKey())
					.setCurrentMemo(details.getMemo())
					.setCurrentExpiry(details.getExpirationTime().getSeconds())
					.setCurrentlyHasProxy(details.hasProxyAccountID())
					.setCurrentNumTokenRels(details.getTokenRelationshipsCount())
					.build();
			return cryptoOpsUsage.cryptoUpdateUsage(txn, sigUsage, ctx);
		} else {
			long now = txn.getTransactionID().getTransactionValidStart().getSeconds();
			return cryptoOpsUsage.cryptoUpdateUsage(txn, sigUsage, missingCtx(now));
		}
	}

	static ExtantCryptoContext missingCtx(long now) {
		return ExtantCryptoContext.newBuilder()
				.setCurrentExpiry(now)
				.setCurrentMemo(DEFAULT_MEMO)
				.setCurrentKey(Key.getDefaultInstance())
				.setCurrentlyHasProxy(false)
				.setCurrentNumTokenRels(0)
				.build();
	}
}
