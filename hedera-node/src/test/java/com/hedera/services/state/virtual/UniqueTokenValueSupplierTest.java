package com.hedera.services.state.virtual;

import org.junit.jupiter.api.Test;

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
}
