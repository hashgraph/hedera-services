package com.hedera.services.bdd.suites.autorenew;

import com.hedera.services.bdd.spec.HapiSpecSetup;

import java.util.Map;

public class AutoRenewConfigChoices {
	static final String defaultMinAutoRenewPeriod =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.autoRenewPeriod.minDuration");
	static final String defaultGracePeriod =
			HapiSpecSetup.getDefaultNodeProps().get("autorenew.gracePeriod");
	static final String defaultNumToScan =
			HapiSpecSetup.getDefaultNodeProps().get("autorenew.numberOfEntitiesToScan");

	static Map<String, String> enablingAutoRenewWith(long minAutoRenewPeriod, long gracePeriod) {
		return Map.of(
				"ledger.autoRenewPeriod.minDuration", "" + minAutoRenewPeriod,
				"autorenew.isEnabled", "true",
				"autorenew.gracePeriod", "" + gracePeriod,
				"autorenew.numberOfEntitiesToScan", "" + 10_000L
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
				"autorenew.gracePeriod", defaultGracePeriod,
				"autorenew.numberOfEntitiesToScan", defaultNumToScan
		);
	}
}
