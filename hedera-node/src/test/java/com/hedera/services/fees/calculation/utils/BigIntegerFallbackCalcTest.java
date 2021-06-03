package com.hedera.services.fees.calculation.utils;

import com.hedera.services.fees.calculation.UsageBasedFeeCalculatorTest;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BigIntegerFallbackCalcTest {
	private BigIntegerFallbackCalc subject = new BigIntegerFallbackCalc();

	@Test
	void matchesLegacyCalc() {
		// given:
		final var legacyFees = FeeBuilder.getFeeObject(mockPrices, mockUsage, mockRate, multiplier);
		// and:
		final var usage = new UsageAccumulator();
		UsageBasedFeeCalculatorTest.copyData(mockUsage, usage);

		// when:
		final var refactoredFees = subject.fees(usage, mockPrices, mockRate, multiplier);

		// then:
		assertEquals(legacyFees.getNodeFee(), refactoredFees.getNodeFee());
		assertEquals(legacyFees.getNetworkFee(), refactoredFees.getNetworkFee());
		assertEquals(legacyFees.getServiceFee(), refactoredFees.getServiceFee());
	}

	private final long multiplier = 2L;
	private final FeeComponents mockFees = FeeComponents.newBuilder()
			.setMax(Long.MAX_VALUE)
			.setConstant(1_234_567L)
			.setBpr(1_000_000L)
			.setBpt(2_000_000L)
			.setRbh(3_000_000L)
			.setSbh(4_000_000L)
			.build();
	private final ExchangeRate mockRate = new ExchangeRates(
			1, 120, 1_234_567L,
			1, 150, 2_345_678L
	).toGrpc().getCurrentRate();
	private FeeData mockPrices = FeeData.newBuilder()
			.setNetworkdata(mockFees)
			.setNodedata(mockFees)
			.setServicedata(mockFees)
			.build();
	private final long one = 1;
	private final long bpt = 2;
	private final long vpt = 3;
	private final long rbh = 4;
	private final long sbh = 5;
	private final long bpr = 8;
	private final long sbpr = 9;
	private final long network_rbh = 10;
	private final FeeComponents mockUsageVector = FeeComponents.newBuilder()
			.setConstant(one)
			.setBpt(bpt)
			.setVpt(vpt)
			.setRbh(rbh)
			.setSbh(sbh)
			.setBpr(bpr)
			.setSbpr(sbpr)
			.build();
	private final FeeData mockUsage = ESTIMATOR_UTILS.withDefaultTxnPartitioning(
			mockUsageVector, network_rbh, 3);
}