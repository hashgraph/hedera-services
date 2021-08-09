package com.hedera.services.sigs.metadata;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.*;

class TokenSigningMetadataTest {
	@Test
	void classifiesRoyaltyWithFallback() {
		// setup:
		var royaltyFeeWithFallbackToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"ZPHYR", "West Wind Art", false, true,
				new EntityId(1, 2, 4));
		royaltyFeeWithFallbackToken.setTokenType(NON_FUNGIBLE_UNIQUE);
		royaltyFeeWithFallbackToken.setFeeSchedule(List.of(
				FcCustomFee.royaltyFee(
						1, 2,
						new FixedFeeSpec(1, null),
						new EntityId(1, 2, 5))));

		// given:
		final var meta = TokenSigningMetadata.from(royaltyFeeWithFallbackToken);

		// expect:
		assertTrue(meta.hasRoyaltyWithFallback());
	}

	@Test
	void classifiesRoyaltyWithNoFallback() {
		// setup:
		var royaltyFeeNoFallbackToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"ZPHYR", "West Wind Art", false, true,
				new EntityId(1, 2, 4));
		royaltyFeeNoFallbackToken.setTokenType(NON_FUNGIBLE_UNIQUE);
		royaltyFeeNoFallbackToken.setFeeSchedule(List.of(
				FcCustomFee.royaltyFee(
						1, 2,
						null,
						new EntityId(1, 2, 5))));

		// given:
		final var meta = TokenSigningMetadata.from(royaltyFeeNoFallbackToken);

		// expect:
		assertFalse(meta.hasRoyaltyWithFallback());
	}
}