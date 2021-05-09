package com.hedera.services.bdd.suites.autorenew;

import com.hedera.services.bdd.spec.HapiSpecSetup;

import java.util.Map;

public class AutoRenewConfigChoices {
	static final String defaultMinAutoRenewPeriod =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.autoRenewPeriod.minDuration");
	static final String defaultGracePeriod =
			HapiSpecSetup.getDefaultNodeProps().get("autorenew.gracePeriod");

	static Map<String, String> enablingAutoRenewWith(long minAutoRenewPeriod, long gracePeriod) {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", "" + minAutoRenewPeriod,
				"autorenew.isEnabled", "true",
				"autorenew.gracePeriod", "" + gracePeriod
		);
	}

	static Map<String, String> leavingAutoRenewDisabledWith(long minAutoRenewPeriod) {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", "" + minAutoRenewPeriod
		);
	}

	static Map<String, String> disablingAutoRenewWithDefaults() {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod,
				"autorenew.isEnabled", "false",
				"autorenew.gracePeriod", defaultGracePeriod
		);
	}
}
