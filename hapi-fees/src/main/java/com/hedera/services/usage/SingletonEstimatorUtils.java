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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECIEPT_STORAGE_TIME_SEC;

public enum SingletonEstimatorUtils implements EstimatorUtils {
	ESTIMATOR_UTILS;

	@Override
	public long baseNetworkRbh() {
		return nonDegenerateDiv(BASIC_RECEIPT_SIZE * RECIEPT_STORAGE_TIME_SEC, HRS_DIVISOR);
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

	@Override
	public FeeComponents.Builder newBaseEstimate(TransactionBody txn, SigUsage sigUsage) {
		return FeeComponents.newBuilder()
				.setBpr(INT_SIZE)
				.setVpt(sigUsage.numSigs())
				.setBpt(baseBodyBytes(txn) + sigUsage.sigsSize())
				.setRbh(nonDegenerateDiv(baseRecordBytes(txn) * RECIEPT_STORAGE_TIME_SEC, HRS_DIVISOR));
	}

	public long nonDegenerateDiv(long dividend, int divisor) {
		return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
	}

	public int baseRecordBytes(TransactionBody txn) {
		return BASIC_TX_RECORD_SIZE + memoBytesUtf8(txn) + transferListBytes(txn.getCryptoTransfer().getTransfers());
	}

	int baseBodyBytes(TransactionBody txn) {
		return BASIC_TX_BODY_SIZE + memoBytesUtf8(txn);
	}

	public static int transferListBytes(TransferList transfers) {
		return BASIC_ACCT_AMT_SIZE * transfers.getAccountAmountsCount();
	}

	public static int memoBytesUtf8(TransactionBody txn) {
		return txn.getMemoBytes().size();
	}

	public static int keyBytes(List<Key> all) {
		return all.stream()
				.mapToInt(FeeBuilder::getAccountKeyStorageSize)
				.sum();
	}

	public static long relativeLifetime(TransactionBody txn, long expiry) {
		long effectiveNow = txn.getTransactionID().getTransactionValidStart().getSeconds();
		return expiry - effectiveNow;
	}
}
