package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class TxnIdSerdeTest extends SelfSerializableDataTest<TxnId> {
	public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<TxnId> getType() {
		return TxnId.class;
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return NUM_TEST_CASES;
	}

	@Override
	protected TxnId getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextTxnId();
	}
}