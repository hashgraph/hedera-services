package com.hedera.services.contracts.execution;

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class LivePricesSourceTest {
	private static final Instant now = Instant.ofEpochSecond(1_234_567L);
	private static final Timestamp timeNow = MiscUtils.asTimestamp(now);
	private static final long gasPriceTinybars = 123;
	private static final long sbhPriceTinybars = 456;
	private static final FeeComponents servicePrices = FeeComponents.newBuilder()
			.setGas(gasPriceTinybars * 1000)
			.setSbh(sbhPriceTinybars * 1000)
			.build();
	private static final FeeData providerPrices = FeeData.newBuilder()
			.setServicedata(servicePrices)
			.build();
	private static final ExchangeRate activeRate = ExchangeRate.newBuilder()
			.setHbarEquiv(1)
			.setCentEquiv(12)
			.build();
	private static final long reasonableMultiplier = 7;
	private static final long insaneMultiplier = Long.MAX_VALUE / 2;

	@Mock
	private HbarCentExchange exchange;
	@Mock
	private UsagePricesProvider usagePrices;
	@Mock
	private FeeMultiplierSource feeMultiplierSource;

	private LivePricesSource subject;

	@BeforeEach
	void setUp() {
		subject = new LivePricesSource(exchange, usagePrices, feeMultiplierSource);
	}

	@Test
	void getsExpectedGasPriceWithReasonableMultiplier() {
		givenCollabsWithMultiplier(reasonableMultiplier);

		final var expected = getTinybarsFromTinyCents(activeRate, gasPriceTinybars) * reasonableMultiplier;

		assertEquals(expected, subject.currentGasPrice(now, ContractCall));
	}

	@Test
	void getsExpectedSbhPriceWithReasonableMultiplier() {
		givenCollabsWithMultiplier(reasonableMultiplier);

		final var expected = getTinybarsFromTinyCents(activeRate, sbhPriceTinybars) * reasonableMultiplier;

		assertEquals(expected, subject.currentStorageByteHoursPrice(now, ContractCall));
	}

	@Test
	void getsExpectedSbhPriceWithInsaneMultiplier() {
		givenCollabsWithMultiplier(insaneMultiplier);

		assertEquals(Long.MAX_VALUE, subject.currentStorageByteHoursPrice(now, ContractCall));
	}

	private void givenCollabsWithMultiplier(final long multiplier) {
		given(exchange.rate(timeNow)).willReturn(activeRate);
		given(usagePrices.defaultPricesGiven(ContractCall, timeNow)).willReturn(providerPrices);
		given(feeMultiplierSource.currentMultiplier()).willReturn(multiplier);
	}
}