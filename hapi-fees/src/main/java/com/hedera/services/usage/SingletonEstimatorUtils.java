package com.hedera.services.usage;

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

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECIEPT_STORAGE_TIME_SEC;

public enum SingletonEstimatorUtils implements EstimatorUtils {
	ESTIMATOR_UTILS;

	@Override
	public long baseNetworkRbs() {
		return BASIC_RECEIPT_SIZE * RECIEPT_STORAGE_TIME_SEC;
	}

	@Override
	public UsageEstimate baseEstimate(TransactionBody txn, SigUsage sigUsage) {
		var base = FeeComponents.newBuilder()
				.setBpr(INT_SIZE)
				.setVpt(sigUsage.numSigs())
				.setBpt(baseBodyBytes(txn) + sigUsage.sigsSize());
		var estimate = new UsageEstimate(base);
		estimate.addRbs(baseRecordBytes(txn) * RECIEPT_STORAGE_TIME_SEC);
		return estimate;
	}

	@Override
	public FeeData withDefaultPartitioning(FeeComponents usage, long networkRbh, int numPayerKeys) {
		var usages = FeeData.newBuilder();

		var network = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(usage.getBpt())
				.setVpt(usage.getVpt())
				.setRbh(networkRbh);
		var node = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(usage.getBpt())
				.setVpt(numPayerKeys)
				.setBpr(usage.getBpr())
				.setSbpr(usage.getSbpr());
		var service = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setRbh(usage.getRbh())
				.setSbh(usage.getSbh())
				.setTv(usage.getTv());
		return usages
				.setNetworkdata(network)
				.setNodedata(node)
				.setServicedata(service)
				.build();
	}

	int baseRecordBytes(TransactionBody txn) {
		return BASIC_TX_RECORD_SIZE
				+ txn.getMemoBytes().size()
				+ transferListBytes(txn.getCryptoTransfer().getTransfers());
	}

	private int transferListBytes(TransferList transfers) {
		return BASIC_ACCT_AMT_SIZE * transfers.getAccountAmountsCount();
	}

}
