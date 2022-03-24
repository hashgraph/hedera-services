package com.hedera.services.state.expiry.renewal;

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;

import java.util.List;

public record TreasuryReturns(List<EntityId> tokenTypes, List<CurrencyAdjustments> transfers, boolean finished) {
	public boolean noneRequired() {
		return tokenTypes.isEmpty();
	}
}
