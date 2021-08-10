package com.hedera.services.bdd.spec.assertions;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

public class NoNftTransfers implements ErroringAssertsProvider<List<TokenTransferList>>  {
	public static NoNftTransfers changingNoNftOwners() {
		return new NoNftTransfers();
	}

	@Override
	public ErroringAsserts<List<TokenTransferList>> assertsFor(HapiApiSpec spec) {
		final List<Throwable> unexpectedOwnershipChanges = new ArrayList<>();
		return tokenTransfers -> {
			for (var tokenTransfer : tokenTransfers) {
				try {
					final var ownershipChanges = tokenTransfer.getNftTransfersList();
					Assertions.assertTrue(
							ownershipChanges.isEmpty(),
							() -> "Expected no NFT transfers, were: " + ownershipChanges);
				} catch (Throwable t) {
					unexpectedOwnershipChanges.add(t);
				}
			}
			return unexpectedOwnershipChanges;
		};
	}
}
