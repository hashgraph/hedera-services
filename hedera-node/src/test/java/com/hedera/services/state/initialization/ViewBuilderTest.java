package com.hedera.services.state.initialization;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ViewBuilderTest {
	private static final EntityId tokenId = new EntityId(0, 0, 54321);
	private static final EntityId ownerId = new EntityId(0, 0, 12345);

	public static FCMap<MerkleUniqueTokenId, MerkleUniqueToken> someUniqueTokens() {
		final var uniqId = new MerkleUniqueTokenId(tokenId, 2);
		final var uniq = new MerkleUniqueToken(
				ownerId,
				"some-metadata".getBytes(),
				RichInstant.fromJava(Instant.ofEpochSecond(1_234_567L, 8)));
		final var ans = new FCMap<MerkleUniqueTokenId, MerkleUniqueToken>();
		ans.put(uniqId, uniq);
		return ans;
	}

	public static void assertIsTheExpectedUta(FCOneToManyRelation<EntityId, MerkleUniqueTokenId> actual) {
		final var uniqId = new MerkleUniqueTokenId(tokenId, 2);
		final var expected = new FCOneToManyRelation<EntityId, MerkleUniqueTokenId>();
		expected.associate(tokenId, uniqId);
		assertEquals(actual.getKeySet(), actual.getKeySet());
		expected.getKeySet().forEach(key -> assertEquals(expected.getList(key), actual.getList(key)));
	}

	public static void assertIsTheExpectedUtao(FCOneToManyRelation<EntityId, MerkleUniqueTokenId> actual) {
		final var uniqId = new MerkleUniqueTokenId(tokenId, 2);
		final var expected = new FCOneToManyRelation<EntityId, MerkleUniqueTokenId>();
		expected.associate(ownerId, uniqId);
		assertEquals(actual.getKeySet(), actual.getKeySet());
		expected.getKeySet().forEach(key -> assertEquals(expected.getList(key), actual.getList(key)));
	}

	@Test
	void rebuildOwnershipsAndAssociationsWorks() {
		// given:
		final var actualUta = new FCOneToManyRelation<EntityId, MerkleUniqueTokenId>();
		final var actualUtao = new FCOneToManyRelation<EntityId, MerkleUniqueTokenId>();

		// when:
		ViewBuilder.rebuildUniqueTokenViews(someUniqueTokens(), actualUta, actualUtao);

		// then:
		assertIsTheExpectedUta(actualUta);
		assertIsTheExpectedUtao(actualUtao);
	}

}
