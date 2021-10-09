package com.hedera.services.bdd.spec.assertions;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
