package com.hedera.services.bdd.suites.utils.validation.domain;

public class FeeSnapshotsScenario {
	final long DEFAULT_TINYBARS_TO_OFFER = 10_000_000_000L;
	final String DEFAULT_SCHEDULE_DESC = "Bootstrap";

	Long tinyBarsToOffer = DEFAULT_TINYBARS_TO_OFFER;
	String scheduleDesc = DEFAULT_SCHEDULE_DESC;
	Boolean ignoreCostAnswer = Boolean.TRUE;
	Boolean appendToSnapshotCsv = Boolean.TRUE;
	SnapshotOpsConfig opsConfig = new SnapshotOpsConfig();

	public Long getTinyBarsToOffer() {
		return tinyBarsToOffer;
	}

	public void setTinyBarsToOffer(Long tinyBarsToOffer) {
		this.tinyBarsToOffer = tinyBarsToOffer;
	}

	public SnapshotOpsConfig getOpsConfig() {
		return opsConfig;
	}

	public void setOpsConfig(SnapshotOpsConfig opsConfig) {
		this.opsConfig = opsConfig;
	}

	public Boolean getAppendToSnapshotCsv() {
		return appendToSnapshotCsv;
	}

	public void setAppendToSnapshotCsv(Boolean appendToSnapshotCsv) {
		this.appendToSnapshotCsv = appendToSnapshotCsv;
	}

	public String getScheduleDesc() {
		return scheduleDesc;
	}

	public void setScheduleDesc(String scheduleDesc) {
		this.scheduleDesc = scheduleDesc;
	}

	public Boolean getIgnoreCostAnswer() {
		return ignoreCostAnswer;
	}

	public void setIgnoreCostAnswer(Boolean ignoreCostAnswer) {
		this.ignoreCostAnswer = ignoreCostAnswer;
	}
}
