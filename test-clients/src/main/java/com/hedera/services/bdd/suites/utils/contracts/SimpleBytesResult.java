package com.hedera.services.bdd.suites.utils.contracts;

import org.apache.tuweni.bytes.Bytes;

public class SimpleBytesResult implements ContractCallResult{
	Bytes assertingBytes;

	private SimpleBytesResult(Bytes result) {
		this.assertingBytes = result;
	}

	public static SimpleBytesResult simpleBytes(Bytes result) {
		return new SimpleBytesResult(result);
	}

	@Override
	public Bytes getBytes() {
		return assertingBytes;
	}
}
