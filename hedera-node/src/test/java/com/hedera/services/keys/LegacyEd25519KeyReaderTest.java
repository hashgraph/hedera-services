package com.hedera.services.keys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEd25519KeyReaderTest {
	private final String expectedABytes = "447dc6bdbfc64eb894851825194744662afcb70efb8b23a6a24af98f0c1fd8ad";
	private final String b64Loc = "src/test/resources/bootstrap/PretendStartupAccount.txt";
	private final String invalidB64Loc = "src/test/resources/bootstrap/PretendStartupAccount.txt";

	LegacyEd25519KeyReader subject = new LegacyEd25519KeyReader();

	@Test
	public void getsExpectedABytes() {
		// expect:
		assertEquals(
				expectedABytes,
				subject.hexedABytesFrom(b64Loc, "START_ACCOUNT"));
	}

	@Test
	public void throwsIaeOnProblem() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.hexedABytesFrom(invalidB64Loc, "NOPE"));
	}
}