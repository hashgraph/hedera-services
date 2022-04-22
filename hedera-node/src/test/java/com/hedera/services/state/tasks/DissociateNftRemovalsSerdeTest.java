package com.hedera.services.state.tasks;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class DissociateNftRemovalsSerdeTest  extends SelfSerializableDataTest<DissociateNftRemovals> {
	public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;
	@Override
	protected Class<DissociateNftRemovals> getType() {
		return DissociateNftRemovals.class;
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return NUM_TEST_CASES;
	}

	@Override
	protected DissociateNftRemovals getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextDissociateNftRemovals();
	}
}
