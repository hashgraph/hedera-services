package com.hedera.services.usage;

import com.hederahashgraph.fee.FeeBuilder;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public enum SingletonUsageProperties implements UsageProperties {
	USAGE_PROPERTIES;

	@Override
	public int accountAmountBytes() {
		return LONG_SIZE + BASIC_ENTITY_ID_SIZE;
	}

	@Override
	public int legacyReceiptStorageSecs() {
		return 180;
	}
}
