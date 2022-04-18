package com.hedera.services.state.merkle.internals;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FilePartSerdeTest extends SelfSerializableDataTest<FilePart> {
	@Override
	protected Class<FilePart> getType() {
		return FilePart.class;
	}

	@Override
	protected FilePart getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextFilePart();
	}
}
