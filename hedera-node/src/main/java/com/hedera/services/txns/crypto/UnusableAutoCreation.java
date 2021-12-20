package com.hedera.services.txns.crypto;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;

public enum UnusableAutoCreation implements AutoCreationLogic {
	UNUSABLE_AUTO_CREATION;

	@Override
	public void setFeeCalculator(final FeeCalculator feeCalculator) {
		/* No-op */
	}

	@Override
	public void reset() {
		/* No-op */
	}

	@Override
	public boolean reclaimPendingAliases() {
		return false;
	}

	@Override
	public void submitRecordsTo(final AccountRecordsHistorian recordsHistorian) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Pair<ResponseCodeEnum, Long> createFromTrigger(final BalanceChange change) {
		throw new UnsupportedOperationException();
	}
}
