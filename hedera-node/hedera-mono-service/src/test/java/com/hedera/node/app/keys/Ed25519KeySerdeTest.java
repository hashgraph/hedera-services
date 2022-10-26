package com.hedera.node.app.keys;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class Ed25519KeySerdeTest extends SelfSerializableDataTest<Ed25519Key> {
	public static final int NUM_TEST_CASES = MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<Ed25519Key> getType() {
		return Ed25519Key.class;
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return NUM_TEST_CASES;
	}

	@Override
	protected Ed25519Key getExpectedObject(
			final SeededPropertySource propertySource) {
		return propertySource.nextEd25519HederaKey();
	}
}
