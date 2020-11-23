package com.hedera.services.bdd.suites.validation.domain;

import java.util.Map;

public class PuvConfig {
	private Map<String, NetworkInfo> networks;

	public Map<String, NetworkInfo> getNetworks() {
		return networks;
	}

	public void setNetworks(Map<String, NetworkInfo> networks) {
		this.networks = networks;
	}
}
