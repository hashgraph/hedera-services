package com.hedera.services.bdd.suiterunner.enums;

// TODO: Contract's string value temporarily renamed from "Contract" to "contract".
//  	 Ensure, that the logic will continue to work ignoring case
public enum SuitePackage {
	ALL("All"),
	AUTORENEW_SUITES("Autorenew"),
	COMPOSE_SUITES("Compose"),
	CONSENSUS_SUITES("Consensus"),
	CONTRACT_SUITES("Contract"),
	CRYPTO_SUITES("Crypto"),
	FEES_SUITES("Fees"),

	FILE_SUITES("File"),
	FREEZE_SUITES("Freeze"),
	ISSUES_SUITES("Issues"),
	META_SUITES("Meta"),
	MISC_SUITES("Misc"),
	PERF_SUITES("Perf"),

	RECONNECT_SUITES("Reconnect"),
	RECORDS_SUITES("Record"),
	REGRESSION_SUITES("Regression"),
	SCHEDULE_SUITES("Schedule"),
	STREAMING_SUITES("Streaming"),
	THROTTLING_SUITES("Throttling"),
	TOKEN_SUITES("Token");

	public final String asString;

	SuitePackage(final String asString) {
		this.asString = asString;
	}
}
