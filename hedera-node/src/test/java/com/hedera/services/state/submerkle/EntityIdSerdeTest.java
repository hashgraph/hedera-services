package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class EntityIdSerdeTest extends SelfSerializableDataTest<EntityId> {
	@Override
	protected Class<EntityId> getType() {
		return EntityId.class;
	}

	@Override
	protected EntityId getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextEntityId();
	}
}
