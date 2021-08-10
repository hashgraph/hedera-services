package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;

public interface FungibleAdjuster {
	BalanceChange adjustedChange(Id account, Id denom, long amount, BalanceChangeManager manager);
}
