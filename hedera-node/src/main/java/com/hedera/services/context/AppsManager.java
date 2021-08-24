package com.hedera.services.context;

import com.hedera.services.ServicesApp;

import java.util.HashMap;
import java.util.Map;

public enum AppsManager {
	APPS;

	private final Map<Long, ServicesApp> apps = new HashMap<>();

	public boolean isInit(long nodeId) {
		return apps.containsKey(nodeId);
	}

	public void init(long id, ServicesApp app) {
		apps.put(id, app);
	}

	public ServicesApp getInit(long id) {
		if (!isInit(id)) {
			throw new IllegalArgumentException("No app initialized for node " + id);
		}
		return apps.get(id);
	}
}
