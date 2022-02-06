package com.hedera.services.stream;

public enum Release023xStreamType implements RecordStreamType {
	RELEASE_023x_STREAM_TYPE;

	private static final int[] RELEASE_023x_FILE_HEADER = new int[] { 5, 0, 23, 0 };

	@Override
	public int[] getFileHeader() {
		return RELEASE_023x_FILE_HEADER;
	}
}
