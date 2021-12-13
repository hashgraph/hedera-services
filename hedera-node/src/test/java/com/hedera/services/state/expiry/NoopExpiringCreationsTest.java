package com.hedera.services.state.expiry;

import org.junit.jupiter.api.Test;

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static org.junit.jupiter.api.Assertions.*;

class NoopExpiringCreationsTest {
	@Test
	void methodsAsExpected() {
		assertDoesNotThrow(() -> NOOP_EXPIRING_CREATIONS.setLedger(null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.saveExpiringRecord(null, null, 0L, 0L));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createTopLevelRecord(0L, null, null, null, null, null, null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createSuccessfulSyntheticRecord(null, null, null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createUnsuccessfulSyntheticRecord(null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createInvalidFailureRecord(null, null));
	}
}