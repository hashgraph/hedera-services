package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FcCustomFeeSerdeTest extends SelfSerializableDataTest<FcCustomFee> {
	@Override
	protected Class<FcCustomFee> getType() {
		return FcCustomFee.class;
	}

	@Override
	protected FcCustomFee getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextCustomFee();
	}
}
