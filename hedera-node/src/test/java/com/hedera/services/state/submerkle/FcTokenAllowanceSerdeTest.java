package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FcTokenAllowanceSerdeTest extends SelfSerializableDataTest<FcTokenAllowance> {
	@Override
	protected Class<FcTokenAllowance> getType() {
		return FcTokenAllowance.class;
	}

	@Override
	protected FcTokenAllowance getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextFcTokenAllowance();
	}
}
