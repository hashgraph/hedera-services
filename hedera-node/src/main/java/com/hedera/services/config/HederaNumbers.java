package com.hedera.services.config;

import com.hedera.services.context.properties.PropertySource;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

public class HederaNumbers {
	private final PropertySource properties;

	private long realm = UNKNOWN_NUMBER;
	private long shard = UNKNOWN_NUMBER;

	public HederaNumbers(PropertySource properties) {
		this.properties = properties;
	}

	public long realm() {
		if (realm == UNKNOWN_NUMBER) {
			realm = properties.getLongProperty("hedera.realm");
		}
		return realm;
	}

	public long shard() {
		if (shard == UNKNOWN_NUMBER) {
			shard = properties.getLongProperty("hedera.shard");
		}
		return shard;
	}
}
