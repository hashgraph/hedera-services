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

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class CryptoTransferUsage extends CryptoTxnUsage<CryptoTransferUsage> {
	private CryptoTransferUsage(TransactionBody tokenTransactOp, TxnUsageEstimator usageEstimator) {
		super(tokenTransactOp, usageEstimator);
	}

	public static CryptoTransferUsage newEstimate(TransactionBody cryptoTransferOp, SigUsage sigUsage) {
		return new CryptoTransferUsage(
				cryptoTransferOp,
				estimatorFactory.get(sigUsage, cryptoTransferOp, ESTIMATOR_UTILS));
	}

	@Override
	public CryptoTransferUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getCryptoTransfer();

		int hbarXfers = op.getTransfers().getAccountAmountsCount();
		int tokenXfers = 0;
		long xferBytes = 0;
		for (TokenTransferList transfer : op.getTokenTransfersList()) {
			xferBytes += BASIC_ENTITY_ID_SIZE;
			tokenXfers += transfer.getTransfersCount();
		}
		xferBytes += (hbarXfers + tokenXfers) * usageProperties.accountAmountBytes();
		usageEstimator.addBpt(xferBytes);
		if (hbarXfers > 0) {
			addRecordRb(hbarXfers * usageProperties.accountAmountBytes());
		}
		addCryptoTransfersRecordRb(op.getTokenTransfersCount(), tokenXfers);

		return usageEstimator.get();
	}
}
