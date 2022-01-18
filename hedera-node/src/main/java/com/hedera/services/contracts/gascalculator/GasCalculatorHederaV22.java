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
import org.hyperledger.besu.evm.Gas;

import javax.inject.Inject;

/**
 * Updates gas costs enabled by gas-per-second throttling.
 */
public class GasCalculatorHederaV22 extends GasCalculatorHederaV19 {

	private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);
	private static final Gas ISTANBUL_TX_DATA_NON_ZERO_COST = Gas.of(16L);
	private static final Gas TX_BASE_COST = Gas.of(21_000L);

	@Inject
	public GasCalculatorHederaV22(final GlobalDynamicProperties dynamicProperties,
								  final UsagePricesProvider usagePrices, final HbarCentExchange exchange) {
		super(dynamicProperties, usagePrices, exchange);
	}

	@Override
	public Gas transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreation) {
		int zeros = 0;
		for (int i = 0; i < payload.size(); i++) {
			if (payload.get(i) == 0) {
				++zeros;
			}
		}
		final int nonZeros = payload.size() - zeros;

		Gas cost =
				TX_BASE_COST
						.plus(TX_DATA_ZERO_COST.times(zeros))
						.plus(ISTANBUL_TX_DATA_NON_ZERO_COST.times(nonZeros));

		return isContractCreation ? cost.plus(txCreateExtraGasCost()) : cost;
	}
}
