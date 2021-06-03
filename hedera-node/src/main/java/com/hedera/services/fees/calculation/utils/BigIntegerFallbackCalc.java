package com.hedera.services.fees.calculation.utils;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.fee.FeeObject;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;

public class BigIntegerFallbackCalc {
	public FeeObject fees(UsageAccumulator usage, FeeData prices, ExchangeRate rate, long multiplier) {
		final long networkFeeTinycents = networkFeeInTinycents(usage, prices.getNetworkdata());
		final long nodeFeeTinycents = nodeFeeInTinycents(usage, prices.getNodedata());
		final long serviceFeeTinycents = serviceFeeInTinycents(usage, prices.getServicedata());

		final long networkFee = tinycentsToTinybars(networkFeeTinycents, rate);
		final long nodeFee = tinycentsToTinybars(nodeFeeTinycents, rate);
		final long serviceFee = tinycentsToTinybars(serviceFeeTinycents, rate);

		return new FeeObject(
				nodeFee * multiplier,
				networkFee * multiplier,
				serviceFee * multiplier);
	}

	private long tinycentsToTinybars(long amount, ExchangeRate rate) {
		return amount * rate.getHbarEquiv() / rate.getCentEquiv();
	}

	private long networkFeeInTinycents(UsageAccumulator usage, FeeComponents networkPrices) {
		final var nominal = networkPrices.getConstant()
				+ usage.getNetworkBpt() * networkPrices.getBpt()
				+ usage.getNetworkVpt() * networkPrices.getVpt()
				+ usage.getNetworkRbh() * networkPrices.getRbh();
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}

	private long nodeFeeInTinycents(UsageAccumulator usage, FeeComponents nodePrices) {
		final var nominal = nodePrices.getConstant()
				+ usage.getNodeBpt() * nodePrices.getBpt()
				+ usage.getNodeBpr() * nodePrices.getBpr()
				+ usage.getNodeSbpr() * nodePrices.getSbpr()
				+ usage.getNodeVpt() * nodePrices.getVpt();
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}

	private long serviceFeeInTinycents(UsageAccumulator usage, FeeComponents servicePrices) {
		final var nominal = servicePrices.getConstant()
				+ usage.getServiceRbh() * servicePrices.getRbh()
				+ usage.getServiceSbh() * servicePrices.getSbh();
		return ESTIMATOR_UTILS.nonDegenerateDiv(nominal, FEE_DIVISOR_FACTOR);
	}
}
