package com.hedera.services.store.tokens.utils;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class GrpcUtilsTest {
	private final GrpcUtils subject = new GrpcUtils();

	@Test
	void createsExpectedForTreasuryUsingWildcard() {
		// given:
		final var treasuryNft = new MerkleUniqueToken(wildcard, treasuryMeta, creationTime);

		// when:
		final var actual = subject.reprOf(
				token.toGrpcTokenId(), treasurySerial, treasuryNft, treasury.toGrpcAccountId());

		// then:
		Assertions.assertEquals(expectedTreasury, actual);
	}

	private final long nonTreasurySerial = 1L;
	private final long treasurySerial = 2L;
	private final byte[] treasuryMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final byte[] nonTreasuryMeta = "...caution to the wind, then.".getBytes(StandardCharsets.UTF_8);
	private final RichInstant creationTime = new RichInstant(1_234_567L, 890);
	private final EntityId token = new EntityId(6, 6, 6);
	private final EntityId wildcard = EntityId.MISSING_ENTITY_ID;
	private final EntityId nonTreasuryOwner = new EntityId(1, 2, 3);
	private final EntityId treasury = new EntityId(2, 3, 4);

	private final TokenNftInfo expectedNonTreasury = TokenNftInfo.newBuilder()
			.setNftID(NftID.newBuilder()
					.setTokenID(token.toGrpcTokenId())
					.setSerialNumber(nonTreasurySerial))
			.setAccountID(nonTreasuryOwner.toGrpcAccountId())
			.setMetadata(ByteString.copyFrom(nonTreasuryMeta))
			.setCreationTime(creationTime.toGrpc())
			.build();

	private final TokenNftInfo expectedTreasury = TokenNftInfo.newBuilder()
			.setNftID(NftID.newBuilder()
					.setTokenID(token.toGrpcTokenId())
					.setSerialNumber(treasurySerial))
			.setAccountID(treasury.toGrpcAccountId())
			.setMetadata(ByteString.copyFrom(treasuryMeta))
			.setCreationTime(creationTime.toGrpc())
			.build();
}