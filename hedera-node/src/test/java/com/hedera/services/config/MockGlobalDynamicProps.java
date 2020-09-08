package com.hedera.services.config;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;

public class MockGlobalDynamicProps extends GlobalDynamicProperties {
	public MockGlobalDynamicProps() {
		super(null);
	}

	@Override
	public void reload() { }

	@Override
	public int maxTokensPerAccount() {
		return 1_000;
	}

	@Override
	public int maxTokenSymbolLength() {
		return 32;
	}

	@Override
	public long maxAccountNum() {
		return 100_000_000L;
	}

	@Override
	public long defaultContractSendThreshold() {
		return 5000000000000000000L;
	}

	@Override
	public long defaultContractReceiveThreshold() {
		return 5000000000000000000L;
	}
}
