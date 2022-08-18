package com.hedera.services.state.expiry;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;

import javax.annotation.Nullable;

public interface RenewalWork {
	boolean tryNextTreasuryReturnFrom(MerkleAccount account);
	boolean tryToRemoveBytecodeFor(EntityNum contract);
	@Nullable MerkleAccount tryToGetNextExpiryCandidate();
}
