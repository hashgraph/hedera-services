package com.hedera.services.bdd.suiterunner.enums;

/*	Note to the reviewer:
 *	Will be used if the developer wants to run the E2E tests by topic
 * */
public enum SuiteTopic {
	ALL("All");

	public final String asString;

	SuiteTopic(final String asString) {
		this.asString = asString;
	}
}
