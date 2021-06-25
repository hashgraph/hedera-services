package com.hedera.services.state.initialization;

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