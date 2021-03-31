package com.hedera.services.files;

import com.hedera.services.files.sysfiles.ConfigCallbacks;
import com.hedera.services.files.sysfiles.CurrencyCallbacks;
import com.hedera.services.files.sysfiles.ThrottlesCallback;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.util.function.Consumer;

public class SysFileCallbacks {
	private final ConfigCallbacks configCallbacks;
	private final ThrottlesCallback throttlesCallback;
	private final CurrencyCallbacks currencyCallbacks;

	public SysFileCallbacks(
			ConfigCallbacks configCallbacks,
			ThrottlesCallback throttlesCallback,
			CurrencyCallbacks currencyCallbacks
	) {
		this.configCallbacks = configCallbacks;
		this.throttlesCallback = throttlesCallback;
		this.currencyCallbacks = currencyCallbacks;
	}

	public Consumer<ExchangeRateSet> exchangeRatesCb() {
		return currencyCallbacks.exchangeRatesCb();
	}

	public Consumer<CurrentAndNextFeeSchedule> feeSchedulesCb() {
		return currencyCallbacks.feeSchedulesCb();
	}

	public Consumer<ThrottleDefinitions> throttlesCb() {
		return throttlesCallback.throttlesCb();
	}

	public Consumer<ServicesConfigurationList> propertiesCb() {
		return configCallbacks.propertiesCb();
	}

	public Consumer<ServicesConfigurationList> permissionsCb() {
		return configCallbacks.permissionsCb();
	}
}
