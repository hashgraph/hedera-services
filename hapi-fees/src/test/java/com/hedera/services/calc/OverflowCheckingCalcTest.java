package com.hedera.services.calc;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverflowCheckingCalcTest {
	private final int rateTinybarComponent = 1001;
	private final int rateTinycentComponent = 1000;
	private final ExchangeRate someRate = ExchangeRate.newBuilder()
			.setHbarEquiv(rateTinybarComponent)
			.setCentEquiv(rateTinycentComponent)
			.build();
	private OverflowCheckingCalc subject = new OverflowCheckingCalc();

	@Test
	void throwsOnMultiplierOverflow() {
		// given:
		final var usage = new UsageAccumulator();
		copyData(mockUsage, usage);

		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.fees(usage, mockPrices, mockRate, Long.MAX_VALUE));
	}

	@Test
	void converterCanFallbackToBigDecimal() {
		// setup:
		final var highFee = Long.MAX_VALUE / rateTinycentComponent;

		// given:
		final var expectedTinybarFee = FeeBuilder.getTinybarsFromTinyCents(someRate, highFee);

		// when:
		final long computedTinybarFee = subject.tinycentsToTinybars(highFee, someRate);

		// then:
		assertEquals(expectedTinybarFee, computedTinybarFee);
	}

	@Test
	void matchesLegacyCalc() {
		// given:
		final var legacyFees = FeeBuilder.getFeeObject(mockPrices, mockUsage, mockRate, multiplier);
		// and:
		final var usage = new UsageAccumulator();
		copyData(mockUsage, usage);

		// when:
		final var refactoredFees = subject.fees(usage, mockPrices, mockRate, multiplier);

		// then:
		assertEquals(legacyFees.getNodeFee(), refactoredFees.getNodeFee());
		assertEquals(legacyFees.getNetworkFee(), refactoredFees.getNetworkFee());
		assertEquals(legacyFees.getServiceFee(), refactoredFees.getServiceFee());
	}

	@Test
	void safeAccumulateTwoWorks() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateTwo(-1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateTwo(1, -1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateTwo(1, 1, -1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateTwo(1, Long.MAX_VALUE, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateTwo(1, 1, Long.MAX_VALUE));

		// and:
		assertEquals(3, subject.safeAccumulateTwo(1, 1, 1));
	}

	@Test
	void safeAccumulateThreeWorks() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(-1, 1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, -1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, 1, -1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, 1, 1, -1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, Long.MAX_VALUE, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, 1, Long.MAX_VALUE, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateThree(1, 1, 1, Long.MAX_VALUE));

		// and:
		assertEquals(4, subject.safeAccumulateThree(1, 1, 1, 1));
	}

	@Test
	void safeAccumulateFourWorks() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(-1, 1, 1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, -1, 1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, -1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, 1, -1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, 1, 1, -1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, Long.MAX_VALUE, 1, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, Long.MAX_VALUE, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, 1, Long.MAX_VALUE, 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.safeAccumulateFour(1, 1, 1, 1, Long.MAX_VALUE));

		// and:
		assertEquals(5, subject.safeAccumulateFour(1, 1, 1, 1, 1));
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
	private final ExchangeRate mockRate = ExchangeRate.newBuilder()
			.setHbarEquiv(1)
			.setCentEquiv(120)
			.build();

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

	public static void copyData(FeeData feeData, UsageAccumulator into) {
		into.setNumPayerKeys(feeData.getNodedata().getVpt());
		into.addVpt(feeData.getNetworkdata().getVpt());
		into.addBpt(feeData.getNetworkdata().getBpt());
		into.addBpr(feeData.getNodedata().getBpr());
		into.addSbpr(feeData.getNodedata().getSbpr());
		into.addNetworkRbs(feeData.getNetworkdata().getRbh() * HRS_DIVISOR);
		into.addRbs(feeData.getServicedata().getRbh() * HRS_DIVISOR);
		into.addSbs(feeData.getServicedata().getSbh() * HRS_DIVISOR);
	}
}
