package com.hedera.services.bdd.suites.utils;

import com.swirlds.common.stream.StreamType;

public class RecordStreamType implements StreamType {
	int RECORD_VERSION = 5;
	String RECORD_DESCRIPTION = "records";
	String RECORD_EXTENSION = "rcd";
	String RECORD_SIG_EXTENSION = "rcd_sig";

	@Override
	public int[] getFileHeader() {
		return new int[0];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDescription() {
		return RECORD_DESCRIPTION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getExtension() {
		return RECORD_EXTENSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getSigExtension() {
		return RECORD_SIG_EXTENSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] getSigFileHeader() {
		return new byte[] { (byte) RECORD_VERSION };
	}
}
