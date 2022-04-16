package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class ExchangeRatesSerdeTest extends SelfSerializableDataTest<ExchangeRates> {
	@Override
	protected Class<ExchangeRates> getType() {
		return ExchangeRates.class;
	}

	@Override
	protected ExchangeRates getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextExchangeRates();
	}
}
