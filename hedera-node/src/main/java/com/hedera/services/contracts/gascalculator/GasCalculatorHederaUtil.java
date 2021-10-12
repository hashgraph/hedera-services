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


import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;

/**
 * Utility methods used by Hedera adapted {@link org.hyperledger.besu.evm.gascalculator.GasCalculator}
 */
final class GasCalculatorHederaUtil {

	private GasCalculatorHederaUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static long ramByteHoursTinyBarsGiven(
			final UsagePricesProvider usagePrices,
			final HbarCentExchange exchange,
			long consensusTime,
			HederaFunctionality functionType) {
		final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
		FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
		long feeInTinyCents = prices.getServicedata().getRbh() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}
}
