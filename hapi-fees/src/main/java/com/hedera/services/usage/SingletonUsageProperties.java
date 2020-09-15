package com.hedera.services.usage;

public enum SingletonUsageProperties implements UsageProperties {
	USAGE_PROPERTIES;

	@Override
	public int legacyReceiptStorageSecs() {
		return 180;
	}
}
