package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FcTokenAllowanceIdSerdeTest extends SelfSerializableDataTest<FcTokenAllowanceId> {
	@Override
	protected Class<FcTokenAllowanceId> getType() {
		return FcTokenAllowanceId.class;
	}

	@Override
	protected FcTokenAllowanceId getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextAllowanceId();
	}
}
