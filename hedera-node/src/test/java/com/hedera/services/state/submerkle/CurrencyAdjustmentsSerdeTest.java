package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class CurrencyAdjustmentsSerdeTest extends SelfSerializableDataTest<CurrencyAdjustments> {
	@Override
	protected Class<CurrencyAdjustments> getType() {
		return CurrencyAdjustments.class;
	}

	@Override
	protected CurrencyAdjustments getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextCurrencyAdjustments();
	}
}
