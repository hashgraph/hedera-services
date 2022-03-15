package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class UniqueTokenValueSupplierTest {
	@Test
	public void tokenSupplier_whenCalledMultipleTimes_producesNewCopies() {
		UniqueTokenValueSupplier supplier = new UniqueTokenValueSupplier();
		UniqueTokenValue value1 = supplier.get();
		UniqueTokenValue value2 = supplier.get();

		assertThat(value1).isNotNull();
		assertThat(value2).isNotNull();
		assertThat(value1).isNotSameInstanceAs(value2);
	}

	// Test invariants. The below tests are designed to fail if one accidentally modifies specified constants.
	@Test
	public void checkClassId_isExpected() {
		assertThat(new UniqueTokenValueSupplier().getClassId()).isEqualTo(0xc4d512c6695451d4L);
	}

	@Test
	public void checkCurrentVersion_isExpected() {
		assertThat(new UniqueTokenValueSupplier().getVersion()).isEqualTo(1);
	}

	@Test
	public void noopFunctions_forTestCoverage() throws IOException {
		UniqueTokenValueSupplier supplier = new UniqueTokenValueSupplier();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(outputStream);
		supplier.serialize(dataOutputStream);
		assertThat(outputStream.toByteArray()).isEmpty();

		SerializableDataInputStream dataInputStream = new SerializableDataInputStream(
				new ByteArrayInputStream(outputStream.toByteArray()));
		supplier.deserialize(dataInputStream, 1);
	}
}
