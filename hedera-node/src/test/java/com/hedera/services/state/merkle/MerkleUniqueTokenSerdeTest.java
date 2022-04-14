package com.hedera.services.state.merkle;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleUniqueTokenSerdeTest extends SelfSerializableDataTest<MerkleUniqueToken> {
	@Override
	protected Class<MerkleUniqueToken> getType() {
		return MerkleUniqueToken.class;
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextMerkleUniqueToken();
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final int version, final int testCaseNo) {
		var expected = super.getExpectedObject(version, testCaseNo);
		if (version < MerkleUniqueToken.RELEASE_0250_VERSION) {
			expected.setSpender(EntityId.MISSING_ENTITY_ID);
		}
		return expected;
	}
}
