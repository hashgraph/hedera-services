package com.hedera.services.bdd.spec.assertions;

import com.hederahashgraph.api.proto.java.TokenAssociation;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutoAssocAsserts {
	public static ErroringAssertsProvider<List<TokenAssociation>> accountTokenPairs(
			final List<Pair<String, String>> expectedAssociations
	) {
		return spec -> actual -> {
			try {
				final var actualNum = actual.size();
				final var expectedNum = expectedAssociations.size();
				assertEquals(
						expectedNum,
						actualNum, "Expected " + expectedNum
								+ " auto-associations, got " + actualNum
								+ " (" + actual + ")");
				int nextActual = 0;
				final var registry = spec.registry();
				for (final var expectedAssoc : expectedAssociations) {
					final var actualAssoc = actual.get(nextActual++);
					final var actualPair = Pair.of(
							actualAssoc.getAccountId(),
							actualAssoc.getTokenId());
					final var expectedPair = Pair.of(
							registry.getAccountID(expectedAssoc.getLeft()),
							registry.getTokenID(expectedAssoc.getRight()));
					assertEquals(expectedPair, actualPair,
							"Wrong auto-association at index " + (nextActual - 1));
				}

				return Collections.emptyList();
			} catch (Throwable t) {
				return List.of(t);
			}
		};
	}
}
