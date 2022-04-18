package com.hedera.services.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleSpecialFilesSerdeTest extends SelfSerializableDataTest<MerkleSpecialFiles> {
	@Override
	protected Class<MerkleSpecialFiles> getType() {
		return MerkleSpecialFiles.class;
	}

	@Override
	protected MerkleSpecialFiles getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextMerkleSpecialFiles();
	}
}
