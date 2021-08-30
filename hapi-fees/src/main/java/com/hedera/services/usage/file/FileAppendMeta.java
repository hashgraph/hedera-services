package com.hedera.services.usage.file;

public class FileAppendMeta {
	private final int bytesAdded;
	private final long lifetime;

	public FileAppendMeta(int bytesAdded, long lifetime) {
		this.bytesAdded = bytesAdded;
		this.lifetime = lifetime;
	}

	public int getBytesAdded() {
		return bytesAdded;
	}

	public long getLifetime() {
		return lifetime;
	}
}
