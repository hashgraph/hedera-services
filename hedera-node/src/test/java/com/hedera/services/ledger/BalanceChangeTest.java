package com.hedera.services.ledger;

import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BalanceChangeTest {
	private final Id a = new Id(1, 2, 3);
	private final Id t = new Id(1, 2, 3);
	private final long delta = -1_234L;

	@Test
	void objectContractSanityChecks() {
		// given:
		final var hbarChange = BalanceChange.hbarAdjust(a, delta);
		final var tokenChange = BalanceChange.tokenAdjust(t, a, delta);
		// and:
		final var hbarRepr = "BalanceChange{token=ℏ, account=Id{shard=1, realm=2, num=3}, units=-1234}";
		final var tokenRepr = "BalanceChange{token=Id{shard=1, realm=2, num=3}, " +
				"account=Id{shard=1, realm=2, num=3}, units=-1234}";

		// expect:
		assertNotEquals(hbarChange, tokenChange);
		assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
		// and:
		assertEquals(hbarRepr, hbarChange.toString());
		assertEquals(tokenRepr, tokenChange.toString());
	}
}