package com.hedera.services.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleEntityIdSerdeTest extends SelfSerializableDataTest<MerkleEntityId> {
	@Override
	protected Class<MerkleEntityId> getType() {
		return MerkleEntityId.class;
	}

	@Override
	protected MerkleEntityId getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextMerkleEntityId();
	}
}
