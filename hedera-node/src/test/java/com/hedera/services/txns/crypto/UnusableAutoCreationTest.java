package com.hedera.services.txns.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnusableAutoCreationTest {
	@Test
	void methodsAsExpected() {
		final var subject = UnusableAutoCreation.UNUSABLE_AUTO_CREATION;

		assertDoesNotThrow(subject::reset);
		assertDoesNotThrow(() -> subject.setFeeCalculator(null));
		assertFalse(subject.reclaimPendingAliases());
		assertThrows(UnsupportedOperationException.class, () -> subject.submitRecordsTo(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.createFromTrigger(null));
	}
}