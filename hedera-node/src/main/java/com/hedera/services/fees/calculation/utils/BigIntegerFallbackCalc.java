package com.hedera.services.fees.calculation.utils;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.fee.FeeObject;

public class BigIntegerFallbackCalc {
	public FeeObject fees(UsageAccumulator usage, FeeData prices, ExchangeRate rate, long multiplier) {
		throw new AssertionError("Not implemented!");
	}
}
