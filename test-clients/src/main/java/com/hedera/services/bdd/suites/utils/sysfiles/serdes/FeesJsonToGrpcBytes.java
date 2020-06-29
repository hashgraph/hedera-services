package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import org.apache.commons.lang3.NotImplementedException;

public class FeesJsonToGrpcBytes implements SysFileSerde<String> {
	@Override
	public String fromRawFile(byte[] bytes) {
		throw new NotImplementedException("TBD");
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		throw new NotImplementedException("TBD");
	}

	@Override
	public String preferredFileName() {
		throw new NotImplementedException("TBD");
	}
}
