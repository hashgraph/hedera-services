package com.hedera.services.state.expiry;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import org.jetbrains.annotations.Nullable;

public class UnThrottledRenewalWork implements RenewalWork{
	@Override
	public boolean tryNextTreasuryReturnFrom(final MerkleAccount account) {
		return false;
	}

	@Override
	public boolean tryToRemoveBytecodeFor(final EntityNum contract) {
		return false;
	}

	@Nullable
	@Override
	public MerkleAccount tryToGetNextExpiryCandidate() {
		return null;
	}
}
