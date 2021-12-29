package com.hedera.services.sigs.metadata;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcCustomFee;

public class TokenMetaUtils {
	private TokenMetaUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static TokenSigningMetadata signingMetaFrom(final MerkleToken token) {
		boolean hasRoyaltyWithFallback = false;
		final var customFees = token.customFeeSchedule();
		if (!customFees.isEmpty()) {
			for (var fee : customFees) {
				if (fee.getFeeType() != FcCustomFee.FeeType.ROYALTY_FEE) {
					continue;
				}
				if (fee.getRoyaltyFeeSpec().fallbackFee() != null) {
					hasRoyaltyWithFallback = true;
					break;
				}
			}
		}
		return new TokenSigningMetadata(
				token.adminKey(),
				token.kycKey(),
				token.wipeKey(),
				token.freezeKey(),
				token.supplyKey(),
				token.feeScheduleKey(),
				token.pauseKey(),
				hasRoyaltyWithFallback,
				token.treasury());
	}
}
