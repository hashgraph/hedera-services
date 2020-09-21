package com.hedera.services.fees.calculation;

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

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.SigValueObj;

import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECIEPT_STORAGE_TIME_SEC;

public class UsageEstimatorUtils {
	public static FeeComponents.Builder withBaseTxnUsage(
			FeeComponents.Builder components,
			SigValueObj sigUsage,
			TransactionBody txn
	) {
		components.setBpr(INT_SIZE);
		components.setVpt(sigUsage.getTotalSigCount());
		components.setBpt(baseBodyBytes(txn) + sigUsage.getSignatureSize());
		components.setRbh(nonDegenerateDiv(baseRecordBytes(txn) * RECIEPT_STORAGE_TIME_SEC, HRS_DIVISOR));

		return components;
	}

	public static long changeInSbsUsage(
			long oldBytesSize, long oldSecsLifetime,
			long newBytesSize, long newSecsLifetime
	) {
		newSecsLifetime = Math.max(oldSecsLifetime, newSecsLifetime);
		long oldSbs = oldBytesSize * oldSecsLifetime;
		long newSbs = newBytesSize * newSecsLifetime;
		return Math.max(0, newSbs - oldSbs);
	}

	public static long relativeLifetime(TransactionBody txn, long expiry) {
		long effectiveNow = txn.getTransactionID().getTransactionValidStart().getSeconds();
		return expiry - effectiveNow;
	}

	public static long nonDegenerateDiv(long dividend, int divisor) {
		return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
	}

	public static int baseRecordBytes(TransactionBody txn) {
		return BASIC_TX_RECORD_SIZE + memoBytesUtf8(txn) + transferListBytes(txn.getCryptoTransfer().getTransfers());
	}

	public static int baseBodyBytes(TransactionBody txn) {
		return BASIC_TX_BODY_SIZE + memoBytesUtf8(txn);
	}

	public static int transferListBytes(TransferList transfers) {
		return BASIC_ACCOUNT_AMT_SIZE * transfers.getAccountAmountsCount();
	}

	public static int memoBytesUtf8(TransactionBody txn) {
		return txn.getMemoBytes().size();
	}

	public static int keyBytes(List<Key> all) {
		return all.stream()
				.mapToInt(FeeBuilder::getAccountKeyStorageSize)
				.sum();
	}

	public static FeeData defaultPartitioning(FeeComponents components, int numPayerKeys) {
		var partitions = FeeData.newBuilder();

		long networkRbh = nonDegenerateDiv(BASIC_RECEIPT_SIZE * RECIEPT_STORAGE_TIME_SEC, HRS_DIVISOR);
		var network = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(components.getBpt())
				.setVpt(components.getVpt())
				.setRbh(networkRbh);

		var node = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(components.getBpt())
				.setVpt(numPayerKeys)
				.setBpr(components.getBpr())
				.setSbpr(components.getSbpr());

		var service = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setRbh(components.getRbh())
				.setSbh(components.getSbh())
				.setTv(components.getTv());

		partitions.setNetworkdata(network).setNodedata(node).setServicedata(service);

		return partitions.build();
	}

	public static FeeComponents.Builder zeroedComponents() {
		return FeeComponents.newBuilder()
				.setBpt(0)
				.setVpt(0)
				.setRbh(0)
				.setSbh(0)
				.setGas(0)
				.setTv(0)
				.setBpr(0)
				.setSbpr(0);
	}
}
