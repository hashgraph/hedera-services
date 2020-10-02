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
		var now = txnCtx.accessor().getTxn().getTransactionID().getTransactionValidStart();
		return rate(now);
	}

	@Override
	public ExchangeRateSet activeRates() {
		return rates;
	}

	@Override
	public ExchangeRate rate(Timestamp at) {
		var currentRate = rates.getCurrentRate();
		long currentExpiry = currentRate.getExpirationTime().getSeconds();
		return (at.getSeconds() < currentExpiry) ? currentRate : rates.getNextRate();
	}

	@Override
	public void updateRates(ExchangeRateSet rates) {
		this.rates = rates;
	}
}
