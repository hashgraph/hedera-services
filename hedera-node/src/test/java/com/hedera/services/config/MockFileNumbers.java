package com.hedera.services.config;

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.FileID;

public class MockFileNumbers extends FileNumbers {
	public MockFileNumbers() {
		super(null);
	}

	@Override
	public boolean isSystem(long num) {
		return num <= 1_000L;
	}

	@Override
	public long addressBook() {
		return 101;
	}

	@Override
	public long nodeDetails() {
		return 102;
	}

	@Override
	public long feeSchedules() {
		return 111;
	}

	@Override
	public long exchangeRates() {
		return 112;
	}

	@Override
	public long applicationProperties() {
		return 121;
	}

	@Override
	public long apiPermissions() {
		return 122;
	}

	@Override
	public FileID toFid(long num) {
		return FileID.newBuilder()
				.setRealmNum(0)
				.setShardNum(0)
				.setFileNum(num)
				.build();
	}
}
