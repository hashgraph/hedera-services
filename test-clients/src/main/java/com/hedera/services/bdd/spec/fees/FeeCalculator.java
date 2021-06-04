package com.hedera.services.bdd.spec.fees;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import static com.hederahashgraph.fee.FeeBuilder.*;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.legacy	.proto.utils.CommonUtils;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FeeCalculator {
	private static final Logger log = LogManager.getLogger(FeeCalculator.class);

	final private HapiSpecSetup setup;
	final private Map<HederaFunctionality, Map<SubType, FeeData>> opFeeData = new HashMap<>();
	final private FeesAndRatesProvider provider;

	private long fixedFee = Long.MIN_VALUE;
	private boolean usingFixedFee = false;

	private int tokenTransferUsageMultiplier = 1;

	public FeeCalculator(HapiSpecSetup setup, FeesAndRatesProvider provider) {
		this.setup = setup;
		this.provider = provider;
	}

	public void init() {
		if (setup.useFixedFee()) {
			usingFixedFee = true;
			fixedFee = setup.fixedFee();
			return;
		}
		FeeSchedule feeSchedule = provider.currentSchedule();
		feeSchedule.getTransactionFeeScheduleList().forEach(feeDataList -> {
			opFeeData.put(feeDataList.getHederaFunctionality(), FeeDataListToMap(feeDataList.getFeeDataListList()));
		});
		tokenTransferUsageMultiplier = setup.feesTokenTransferUsageMultiplier();
	}

	private Map<SubType, FeeData> FeeDataListToMap(List<FeeData> feeDataList) {
		Map<SubType, FeeData> feeDataMap = new HashMap<>();
		for (FeeData feeData : feeDataList) {
			feeDataMap.put(feeData.getSubType(), feeData);
		}
		return feeDataMap;
	}

	public long maxFeeTinyBars(SubType subType) {
		return usingFixedFee ? fixedFee : Arrays
				.stream(HederaFunctionality.values())
				.mapToLong(op ->
						Optional.ofNullable(
								opFeeData.get(op)).map(fd ->
								fd.get(subType).getServicedata().getMax()
										+ fd.get(subType).getNodedata().getMax()
										+ fd.get(subType).getNetworkdata().getMax()).orElse(0L))
				.max()
				.orElse(0L);
	}

	public long maxFeeTinyBars() {
		return maxFeeTinyBars(SubType.DEFAULT);
	}

	public long forOp(HederaFunctionality op, SubType subType, FeeData knownActivity) {
		if (usingFixedFee) {
			return fixedFee;
		}
		try {
			Map<SubType, FeeData> activityPrices = opFeeData.get(op);
			return getTotalFeeforRequest(activityPrices.get(subType), knownActivity, provider.rates());
		} catch (Throwable t) {
			log.warn("Unable to calculate fee for op {}, using max fee!", op, t);
		}
		return maxFeeTinyBars(subType);
	}

	public long forOp(HederaFunctionality op, FeeData knownActivity) {
		return forOp(op, SubType.DEFAULT, knownActivity);
	}

	@FunctionalInterface
	public interface ActivityMetrics {
		FeeData compute(TransactionBody body, SigValueObj sigUsage) throws Throwable;
	}

	public long forActivityBasedOp(
			HederaFunctionality op,
			ActivityMetrics metricsCalculator,
			Transaction txn,
			int numPayerSigs
	) throws Throwable {
		FeeData activityMetrics = metricsFor(txn, numPayerSigs, metricsCalculator);
		return forOp(op, SubType.DEFAULT, activityMetrics);
	}

	public long forActivityBasedOp(
			HederaFunctionality op,
			SubType subType,
			ActivityMetrics metricsCalculator,
			Transaction txn,
			int numPayerSigs
	) throws Throwable {
		FeeData activityMetrics = metricsFor(txn, numPayerSigs, metricsCalculator);
		return forOp(op, subType, activityMetrics);
	}

	private FeeData metricsFor(
			Transaction txn,
			int numPayerSigs,
			ActivityMetrics metricsCalculator
	) throws Throwable {
		SigValueObj sigUsage = sigUsageGiven(txn, numPayerSigs);
		TransactionBody body = CommonUtils.extractTransactionBody(txn);
		return metricsCalculator.compute(body, sigUsage);
	}

	private SigValueObj sigUsageGiven(Transaction txn, int numPayerSigs) {
		int size = getSignatureSize(txn);
		int totalNumSigs = getSignatureCount(txn);
		return new SigValueObj(totalNumSigs, numPayerSigs, size);
	}

	public int tokenTransferUsageMultiplier() {
		return tokenTransferUsageMultiplier;
	}
}
