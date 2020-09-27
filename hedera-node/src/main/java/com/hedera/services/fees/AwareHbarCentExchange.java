package com.hedera.services.fees;

import com.hedera.services.context.TransactionContext;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;

public class AwareHbarCentExchange implements HbarCentExchange {
	private static final ExchangeRateSet UNKNOWN_RATES = null;

	private final TransactionContext txnCtx;

	ExchangeRateSet rates = UNKNOWN_RATES;

	public AwareHbarCentExchange(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	@Override
	public ExchangeRate activeRate() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public ExchangeRateSet activeRates() {
		throw new AssertionError("Not implemented");
	}

	@Override
	public ExchangeRate rate(Timestamp at) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void updateRates(ExchangeRateSet rates) {
		throw new AssertionError("Not implemented");
	}
}
