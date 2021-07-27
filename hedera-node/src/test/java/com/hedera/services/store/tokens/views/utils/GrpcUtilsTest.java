package com.hedera.services.store.tokens.views.utils;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GrpcUtilsTest {
	@Test
	void createsExpectedForTreasuryUsingWildcard() {
		final var treasuryNft = new MerkleUniqueToken(wildcard, meta, creationTime);

		final var actual = GrpcUtils.reprOf(token, treasurySerial, treasuryNft, treasury.toGrpcAccountId());

		assertEquals(expectedNftInfo(treasurySerial, treasury), actual);
	}

	@Test
	void throwsOnNullTreasury() {
		final var nft = mock(MerkleUniqueToken.class);
		given(nft.isTreasuryOwned()).willReturn(true);

		final var iae = assertThrows(IllegalArgumentException.class,
				() -> GrpcUtils.reprOf(token, treasurySerial, nft, null));

		assertEquals("6.6.6.2 has wildcard owner, but no treasury information was provided", iae.getMessage());
	}

	@Test
	void createsExpectedForOwner() {
		final var nft = new MerkleUniqueToken(nonTreasuryOwner, meta, creationTime);

		final var actual = GrpcUtils.reprOf(token, nonTreasurySerial, nft, null);

		assertEquals(expectedNftInfo(nonTreasurySerial, nonTreasuryOwner), actual);
	}

	@Test
	void throwsInConstructor() {
		assertThrows(IllegalStateException.class, () -> new GrpcUtils());
	}

	@Test
	void createsExpectedForNonTreasuryUsingWildcard() {
		final var nonTreasuryNft = new MerkleUniqueToken(nonTreasuryOwner, nonTreasuryMeta, creationTime);

		final var actual = subject.reprOf(
				token.toGrpcTokenId(), nonTreasurySerial, nonTreasuryNft, nonTreasuryOwner.toGrpcAccountId());

		Assertions.assertEquals(expectedNonTreasury, actual);
	}

	@Test
	void failsAsExpectedWhenTreasuryIsNull() {
		final var treasuryNft = new MerkleUniqueToken(wildcard, treasuryMeta, creationTime);

		Assertions.assertThrows(IllegalArgumentException.class, () -> subject.reprOf(
				token.toGrpcTokenId(), treasurySerial, treasuryNft, null));
	}

	private final long nonTreasurySerial = 1L;
	private final long treasurySerial = 2L;
	private final byte[] meta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final RichInstant creationTime = new RichInstant(1_234_567L, 890);
	private final TokenID token = new EntityId(6, 6, 6).toGrpcTokenId();
	private final EntityId wildcard = EntityId.MISSING_ENTITY_ID;
	private final EntityId nonTreasuryOwner = new EntityId(1, 2, 3);
	private final EntityId treasury = new EntityId(2, 3, 4);

	private TokenNftInfo expectedNftInfo(final long serial, final EntityId owner) {
		return TokenNftInfo.newBuilder()
				.setNftID(NftID.newBuilder()
						.setTokenID(token)
						.setSerialNumber(serial))
				.setAccountID(owner.toGrpcAccountId())
				.setMetadata(ByteString.copyFrom(meta))
				.setCreationTime(creationTime.toGrpc())
				.build();
	}
	private final TokenNftInfo expectedTreasury = TokenNftInfo.newBuilder()
			.setNftID(NftID.newBuilder()
					.setTokenID(token.toGrpcTokenId())
					.setSerialNumber(treasurySerial))
			.setAccountID(treasury.toGrpcAccountId())
			.setMetadata(ByteString.copyFrom(treasuryMeta))
			.setCreationTime(creationTime.toGrpc())
			.build();

	private final TokenNftInfo expectedNonTreasury = TokenNftInfo.newBuilder()
			.setNftID(NftID.newBuilder()
					.setTokenID(token.toGrpcTokenId())
					.setSerialNumber(nonTreasurySerial))
			.setAccountID(nonTreasuryOwner.toGrpcAccountId())
			.setMetadata(ByteString.copyFrom(nonTreasuryMeta))
			.setCreationTime(creationTime.toGrpc())
			.build();
}
