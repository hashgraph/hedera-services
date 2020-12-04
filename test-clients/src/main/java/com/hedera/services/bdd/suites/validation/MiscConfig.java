package com.hedera.services.bdd.suites.validation;

public class MiscConfig {
	private final boolean leaveManifestsAlone;

	public MiscConfig(boolean leaveManifestsAlone) {
		this.leaveManifestsAlone = leaveManifestsAlone;
	}

	public String updateCreatedManifests() {
		return leaveManifestsAlone ? "false" : "true";
	}
}
