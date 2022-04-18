package com.hedera.services.stream;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class RecordsRunningHashLeafSerdeTest extends SelfSerializableDataTest<RecordsRunningHashLeaf> {
	@Override
	protected Class<RecordsRunningHashLeaf> getType() {
		return RecordsRunningHashLeaf.class;
	}

	@Override
	protected RecordsRunningHashLeaf getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextRecordsRunningHashLeaf();
	}
}
