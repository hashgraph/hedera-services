package com.hedera.services.contracts.gascalculator;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

import javax.inject.Inject;

/**
 * Provides Hedera adapted gas cost lookups and calculations used during transaction processing.
 * Maps the gas costs of the Smart Contract Service including and after 0.19.0 release
 */
public class GasCalculatorHederaV19 extends LondonGasCalculator {

	private static final int LOG_CONTRACT_ID_SIZE = 24;
	private static final int LOG_TOPIC_SIZE = 32;
	private static final int LOG_BLOOM_SIZE = 256;

	private final GlobalDynamicProperties dynamicProperties;
	private final UsagePricesProvider usagePrices;
	private final HbarCentExchange exchange;

	@Inject
	public GasCalculatorHederaV19(
			final GlobalDynamicProperties dynamicProperties,
			final UsagePricesProvider usagePrices,
			final HbarCentExchange exchange) {
		this.dynamicProperties = dynamicProperties;
		this.usagePrices = usagePrices;
		this.exchange = exchange;
	}

	@Override
	public Gas codeDepositGasCost(final int codeSize) {
		return Gas.ZERO;
	}

	@Override
	public Gas transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreate) {
		return Gas.ZERO;
	}

	@Override
	public Gas logOperationGasCost(
			final MessageFrame frame,
			final long dataOffset,
			final long dataLength,
			final int numTopics) {
		long logStorageTotalSize = calculateLogSize(numTopics, dataLength);
		long gasPrice = frame.getGasPrice().toLong();
		long timestamp = frame.getBlockValues().getTimestamp();
		HederaFunctionality functionType = getFunctionType(frame);
		long gasCost = calculateStorageGasNeeded(
				logStorageTotalSize,
				getLogStorageDuration(),
				ramByteHoursTinyBarsGiven(timestamp, functionType),
				gasPrice);
		return super.logOperationGasCost(frame, dataOffset, dataLength, numTopics).max(Gas.of(gasCost));
	}

	protected long ramByteHoursTinyBarsGiven(long consensusTime, HederaFunctionality functionType) {
		return GasCalculatorHederaUtil.ramByteHoursTinyBarsGiven(usagePrices, exchange, consensusTime, functionType);
	}

	private HederaFunctionality getFunctionType(MessageFrame frame) {
		MessageFrame rootFrame = frame.getMessageFrameStack().getLast();
		return rootFrame.getContextVariable("HederaFunctionality");
	}

	public static long calculateLogSize(int numberOfTopics, long dataSize) {
		return LOG_CONTRACT_ID_SIZE + LOG_BLOOM_SIZE + LOG_TOPIC_SIZE * (long) numberOfTopics + dataSize;
	}

	@SuppressWarnings("unused")
	public static long calculateStorageGasNeeded(
			long numberOfBytes,
			long durationInSeconds,
			long byteHourCostIntinybars,
			long gasPrice
	) {
		long storageCostTinyBars = (durationInSeconds * byteHourCostIntinybars) / 3600;
		return Math.round((double) storageCostTinyBars / (double) gasPrice);
	}

	long getLogStorageDuration() {
		return dynamicProperties.cacheRecordsTtl();
	}
}
