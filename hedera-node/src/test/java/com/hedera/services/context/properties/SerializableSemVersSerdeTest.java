package com.hedera.services.context.properties;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

import java.util.Optional;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializableSemVersSerdeTest extends SelfSerializableDataTest<SerializableSemVers> {
	@Override
	protected Class<SerializableSemVers> getType() {
		return SerializableSemVers.class;
	}

	@Override
	protected SerializableSemVers getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextSerializableSemVers();
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return 2 * MIN_TEST_CASES_PER_VERSION;
	}

	@Override
	protected Optional<BiConsumer<SerializableSemVers, SerializableSemVers>> customAssertEquals() {
		return Optional.of(SerializableSemVersSerdeTest::assertEqualVersions);
	}

	public static void assertEqualVersions(final SerializableSemVers a, final SerializableSemVers b) {
		assertEquals(a.getProto(), b.getProto(), "protobuf semvers are unequal");
		assertEquals(a.getServices(), b.getServices(), "Services semvers are unequal");
	}
}