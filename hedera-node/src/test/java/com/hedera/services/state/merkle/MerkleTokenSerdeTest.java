package com.hedera.services.state.merkle;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;

public class MerkleTokenSerdeTest extends SelfSerializableDataTest<MerkleToken> {
	public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
	}

	@Override
	protected Class<MerkleToken> getType() {
		return MerkleToken.class;
	}

	@Override
	protected int getNumTestCasesFor(int version) {
		return NUM_TEST_CASES;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(MerkleToken.class, version, testCaseNo);
	}

	@Override
	protected MerkleToken getExpectedObject(final int version, final int testCaseNo) {
		return SeededPropertySource.forSerdeTest(version, testCaseNo).nextToken();
	}

	@Override
	protected MerkleToken getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextToken();
	}
}