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
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;

import javax.inject.Inject;

/**
 * Provides Hedera adapted gas cost lookups and calculations used during transaction processing.
 * Maps to the gas costs of the Smart Contract Service up until 0.18.0 release
 */
public class GasCalculatorHederaV18 extends PetersburgGasCalculator {
	private final GlobalDynamicProperties dynamicProperties;
	private final UsagePricesProvider usagePrices;
	private final HbarCentExchange exchange;

	@Inject
	public GasCalculatorHederaV18(
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
		return GasCalculatorHederaUtil.logOperationGasCost(usagePrices, exchange, frame, getLogStorageDuration(), dataOffset, dataLength, numTopics);
	}

	@Override
	public Gas getBalanceOperationGasCost() {
		// Frontier gas cost
		return Gas.of(20L);
	}

	@Override
	protected Gas expOperationByteGasCost() {
		// Frontier gas cost
		return Gas.of(10L);
	}

	@Override
	protected Gas extCodeBaseGasCost() {
		// Frontier gas cost
		return Gas.of(20L);
	}

	@Override
	public Gas getSloadOperationGasCost() {
		// Frontier gas cost
		return Gas.of(50L);
	}

	@Override
	public Gas callOperationBaseGasCost() {
		// Frontier gas cost
		return Gas.of(40L);
	}

	@Override
	public Gas getExtCodeSizeOperationGasCost() {
		// Frontier gas cost
		return Gas.of(20L);
	}

	@Override
	public Gas extCodeHashOperationGasCost() {
		// Constantinople gas cost
		return Gas.of(400L);
	}

	@Override
	public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
		// Frontier gas cost
		return Gas.of(0);
	}

	private long getLogStorageDuration() {
		return dynamicProperties.cacheRecordsTtl();
	}
}
