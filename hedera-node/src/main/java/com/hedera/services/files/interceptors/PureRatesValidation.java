package com.hedera.services.files.interceptors;

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

import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hedera.services.state.submerkle.ExchangeRates;
import org.junit.Test;

import java.math.BigInteger;
import java.util.stream.LongStream;
import static java.math.BigInteger.valueOf;


public class PureRatesValidation {
	private static BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

	PureRatesValidation(){
		throw new IllegalStateException();
	}

	public static boolean isNormalIntradayChange(
			ExchangeRates midnightRates,
			ExchangeRateSet proposedRates,
			int limitPercent
	) {
		return canonicalTest(
				limitPercent,
				midnightRates.getCurrCentEquiv(), midnightRates.getCurrHbarEquiv(),
				proposedRates.getCurrentRate().getCentEquiv(), proposedRates.getCurrentRate().getHbarEquiv())
				&& canonicalTest(
						limitPercent,
						midnightRates.getNextCentEquiv(), midnightRates.getNextHbarEquiv(),
						proposedRates.getNextRate().getCentEquiv(), proposedRates.getNextRate().getHbarEquiv());
	}

	private static boolean canonicalTest(long bound, long oldC, long oldH, long newC, long newH) {
		var b100 = valueOf(bound).add(ONE_HUNDRED);

		var oC = valueOf(oldC);
		var oH = valueOf(oldH);
		var nC = valueOf(newC);
		var nH = valueOf(newH);

		return LongStream.of(bound, oldC, oldH, newC, newH).allMatch(i -> i > 0) &&
				oC.multiply(nH)
						.multiply(b100)
						.subtract(nC.multiply(oH).multiply(ONE_HUNDRED)).signum() >= 0 &&
				oH.multiply(nC)
						.multiply(b100)
						.subtract(nH.multiply(oC).multiply(ONE_HUNDRED)).signum() >= 0;
	}
}
