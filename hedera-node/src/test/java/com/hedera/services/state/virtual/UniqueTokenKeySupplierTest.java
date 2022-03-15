package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class UniqueTokenKeySupplierTest {
	@Test
	public void tokenSupplier_whenCalledMultipleTimes_producesNewCopies() {
		UniqueTokenKeySupplier supplier = new UniqueTokenKeySupplier();
		UniqueTokenKey key1 = supplier.get();
		UniqueTokenKey key2 = supplier.get();
		assertThat(key1).isNotNull();
		assertThat(key2).isNotNull();
		assertThat(key1).isNotSameInstanceAs(key2);
	}

	// Test invariants. The below tests are designed to fail if one accidentally modifies specified constants.
	@Test
	public void checkClassId_isExpected() {
		assertThat(new UniqueTokenKeySupplier().getClassId()).isEqualTo(0x8232d5e6ed77cc5cL);
	}

	@Test
	public void checkCurrentVersion_isExpected() {
		assertThat(new UniqueTokenKeySupplier().getVersion()).isEqualTo(1);
	}


	@Test
	public void noopFunctions_forTestCoverage() throws IOException {
		UniqueTokenKeySupplier supplier = new UniqueTokenKeySupplier();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(outputStream);
		supplier.serialize(dataOutputStream);
		assertThat(outputStream.toByteArray()).isEmpty();

		SerializableDataInputStream dataInputStream = new SerializableDataInputStream(
				new ByteArrayInputStream(outputStream.toByteArray()));
		supplier.deserialize(dataInputStream, 1);
	}
}
