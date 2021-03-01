package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.suites.HapiApiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;

public class SimpleXfer {
	static final long DEFAULT_TINYBARS = 1L;

	private long amount = DEFAULT_TINYBARS;
	private String to = FUNDING;
	private String from = GENESIS;

	public long getAmount() {
		return amount;
	}

	public void setAmount(long amount) {
		this.amount = amount;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}
}
