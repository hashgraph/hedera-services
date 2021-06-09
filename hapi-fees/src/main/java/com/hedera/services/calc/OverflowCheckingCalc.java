package com.hedera.services.calc;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FeeObject;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;

public class OverflowCheckingCalc {
	public FeeObject fees(UsageAccumulator usage, FeeData prices, ExchangeRate rate, long multiplier) {
		final long networkFeeTinycents = networkFeeInTinycents(usage, prices.getNetworkdata());
		final long nodeFeeTinycents = nodeFeeInTinycents(usage, prices.getNodedata());
		final long serviceFeeTinycents = serviceFeeInTinycents(usage, prices.getServicedata());

		final long networkFee = tinycentsToTinybars(networkFeeTinycents, rate) * multiplier;
		final long nodeFee = tinycentsToTinybars(nodeFeeTinycents, rate) * multiplier;
		final long serviceFee = tinycentsToTinybars(serviceFeeTinycents, rate) * multiplier;

		if (networkFee < 0 || nodeFee < 0 || serviceFee < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}

		return new FeeObject(nodeFee, networkFee, serviceFee);
	}

	long tinycentsToTinybars(long amount, ExchangeRate rate) {
		final var product = amount * rate.getHbarEquiv();
		if (product < 0) {
			return FeeBuilder.getTinybarsFromTinyCents(rate, amount);
		}
		return product / rate.getCentEquiv();
	}

	private long networkFeeInTinycents(UsageAccumulator usage, FeeComponents networkPrices) {
		final var nominal = safeAccumulateThree(networkPrices.getConstant(),
				usage.getUniversalBpt() * networkPrices.getBpt(),
				usage.getNetworkVpt() * networkPrices.getVpt(),
				usage.getNetworkRbh() * networkPrices.getRbh());
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}


	private long nodeFeeInTinycents(UsageAccumulator usage, FeeComponents nodePrices) {
		final var nominal = safeAccumulateFour(nodePrices.getConstant(),
				usage.getUniversalBpt() * nodePrices.getBpt(),
				usage.getNodeBpr() * nodePrices.getBpr(),
				usage.getNodeSbpr() * nodePrices.getSbpr(),
				usage.getNodeVpt() * nodePrices.getVpt());
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}

	private long serviceFeeInTinycents(UsageAccumulator usage, FeeComponents servicePrices) {
		final var nominal = safeAccumulateTwo(servicePrices.getConstant(),
				usage.getServiceRbh() * servicePrices.getRbh(),
				usage.getServiceSbh() * servicePrices.getSbh());
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}

	/* These verbose accumulators signatures are to avoid any performance hit from varargs */
	long safeAccumulateFour(long base, long a, long b, long c, long d) {
		if (base < 0 || a < 0 || b < 0 || c < 0 || d < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		var sum = safeAccumulateThree(base, a, b, c);
		sum += d;
		if (sum < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		return sum;
	}

	long safeAccumulateThree(long base, long a, long b, long c) {
		if (base < 0 || a < 0 || b < 0 || c < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		var sum = safeAccumulateTwo(base, a, b);
		sum += c;
		if (sum < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		return sum;
	}

	long safeAccumulateTwo(long base, long a, long b) {
		if (base < 0 || a < 0 || b < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		base += a;
		if (base < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		base += b;
		if (base < 0) {
			throw new IllegalArgumentException("All fee contributions and intermediate terms must be positive");
		}
		return base;
	}

}
