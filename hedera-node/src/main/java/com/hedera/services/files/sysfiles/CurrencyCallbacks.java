package com.hedera.services.files.sysfiles;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CurrencyCallbacks {
	private final FeeCalculator fees;
	private final HbarCentExchange exchange;
	private final Supplier<ExchangeRates> midnightRates;

	public CurrencyCallbacks(FeeCalculator fees, HbarCentExchange exchange, Supplier<ExchangeRates> midnightRates) {
		this.fees = fees;
		this.exchange = exchange;
		this.midnightRates = midnightRates;
	}

	public Consumer<ExchangeRateSet> exchangeRatesCb() {
		return rates -> {
			exchange.updateRates(rates);
			var curMidnightRates = midnightRates.get();
			if (!curMidnightRates.isInitialized()) {
				curMidnightRates.replaceWith(rates);
			}
		};
	}

	public Consumer<CurrentAndNextFeeSchedule> feeSchedulesCb() {
		return ignore -> fees.init();
	}
}
