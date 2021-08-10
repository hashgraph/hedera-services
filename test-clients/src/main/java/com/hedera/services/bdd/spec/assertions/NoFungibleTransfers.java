package com.hedera.services.bdd.spec.assertions;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

public class NoFungibleTransfers implements ErroringAssertsProvider<List<TokenTransferList>> {
	public static NoFungibleTransfers changingNoFungibleBalances() {
		return new NoFungibleTransfers();
	}

	@Override
	public ErroringAsserts<List<TokenTransferList>> assertsFor(HapiApiSpec spec) {
		final List<Throwable> unexpectedFungibleChanges = new ArrayList<>();
		return tokenTransfers -> {
			for (var tokenTransfer : tokenTransfers) {
				try {
					final var fungibleChanges = tokenTransfer.getTransfersList();
					Assertions.assertTrue(
							fungibleChanges.isEmpty(),
							() -> "Expected no fungible balance changes, were: " + fungibleChanges);
				} catch (Throwable t) {
					unexpectedFungibleChanges.add(t);
				}
			}
			return unexpectedFungibleChanges;
		};
	}
}
