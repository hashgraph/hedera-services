package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class NftAdjustmentsSerdeTest extends SelfSerializableDataTest<NftAdjustments> {
	@Override
	protected Class<NftAdjustments> getType() {
		return NftAdjustments.class;
	}

	@Override
	protected NftAdjustments getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextOwnershipChanges();
	}
}
