package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.WorldLedgers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PrecompileDefaultsTest {
	@Mock
	private WorldLedgers worldLedgers;

	@Test
	void customizationIsNoop() {
		final var subject = mock(Precompile.class);

		doCallRealMethod().when(subject).customizeTrackingLedgers(worldLedgers);

		assertDoesNotThrow(() -> subject.customizeTrackingLedgers(worldLedgers));

		verifyNoInteractions(worldLedgers);
	}
}