package com.hedera.services.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleTokenRelStatusSerdeTest extends SelfSerializableDataTest<MerkleTokenRelStatus> {
	@Override
	protected Class<MerkleTokenRelStatus> getType() {
		return MerkleTokenRelStatus.class;
	}

	@Override
	protected MerkleTokenRelStatus getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextMerkleTokenRelStatus();
	}

	@Override
	protected MerkleTokenRelStatus getExpectedObject(final int version, final int testCaseNo) {
		var expected = super.getExpectedObject(version, testCaseNo);
		if (version < MerkleTokenRelStatus.RELEASE_0250_VERSION) {
			expected.setNext(0);
			expected.setPrev(0);
		}
		return expected;
	}
}
