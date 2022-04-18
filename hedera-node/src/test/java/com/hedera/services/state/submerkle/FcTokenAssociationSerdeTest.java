package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FcTokenAssociationSerdeTest extends SelfSerializableDataTest<FcTokenAssociation> {
	@Override
	protected Class<FcTokenAssociation> getType() {
		return FcTokenAssociation.class;
	}

	@Override
	protected FcTokenAssociation getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextTokenAssociation();
	}
}
