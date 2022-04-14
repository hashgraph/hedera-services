package com.hedera.services.state.merkle;

import com.hedera.services.utils.NftNumPair;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

import static com.hedera.services.state.merkle.MerkleUniqueToken.RELEASE_0250_VERSION;

public class MerkleUniqueTokenSerdeTest extends SelfSerializableDataTest<MerkleUniqueToken> {
	public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<MerkleUniqueToken> getType() {
		return MerkleUniqueToken.class;
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextUniqueToken();
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final int version, final int testCaseNo) {
		final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
		final var seededObject = getExpectedObject(propertySource);
		if (version == RELEASE_0250_VERSION) {
			seededObject.setPrev(NftNumPair.MISSING_NFT_NUM_PAIR);
			seededObject.setNext(NftNumPair.MISSING_NFT_NUM_PAIR);
			return seededObject;
		} else {
			return seededObject;
		}
	}
}
