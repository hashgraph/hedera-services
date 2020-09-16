package com.hedera.services.config;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.AccountID;

public class MockGlobalDynamicProps extends GlobalDynamicProperties {
	public MockGlobalDynamicProps() {
		super(null, null);
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
	public int maxTokensNameLength() {
		return 100;
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

	@Override
	public int maxFileSizeKb() {
		return 1024;
	}

	@Override
	public AccountID fundingAccount() {
		return AccountID.newBuilder().setAccountNum(98L).build();
	}

	@Override
	public int cacheRecordsTtl() {
		return 180;
	}

	@Override
	public int maxContractStorageKb() {
		return 1024;
	}

	@Override
	public int ratesIntradayChangeLimitPercent() {
		return 5;
	}
}
