package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.virtual.ContractKey;

public record SlotRemovalOutcome(int numRemoved, ContractKey newRoot) {
}
