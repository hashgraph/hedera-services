package com.hedera.services.usage.file;

import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.fee.FeeBuilder;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.usage.file.FileOpsUsage.asKey;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class FileUpdateContext {
	private final long currentSize;
	private final long currentExpiry;
	private final String currentMemo;
	private final KeyList currentWacl;

	private FileUpdateContext(FileUpdateContext.Builder builder) {
		currentMemo = builder.currentMemo;
		currentSize = builder.currentSize;
		currentExpiry = builder.currentExpiry;
		currentWacl = builder.currentWacl;
	}

	public long currentNonBaseSb() {
		return currentSize
				+ currentMemo.getBytes(StandardCharsets.UTF_8).length
				+ getAccountKeyStorageSize(asKey(currentWacl));
	}

	public long currentSize() {
		return currentSize;
	}

	public long currentExpiry() {
		return currentExpiry;
	}

	public String currentMemo() {
		return currentMemo;
	}

	public KeyList currentWacl() {
		return currentWacl;
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static class Builder {
		private static final int SIZE_MASK = 1 << 0;
		private static final int EXPIRY_MASK = 1 << 1;
		private static final int MEMO_MASK = 1 << 2;
		private static final int WACL_MASK = 1 << 3;

		private static final int ALL_FIELDS_MASK = SIZE_MASK | EXPIRY_MASK | MEMO_MASK | WACL_MASK;

		private int mask = 0;
		private long currentSize;
		private long currentExpiry;
		private String currentMemo;
		private KeyList currentWacl;

		private Builder() {}

		public FileUpdateContext build() {
			if (mask != ALL_FIELDS_MASK) {
				throw new IllegalStateException(String.format("Field mask is %d, not %d!", mask, ALL_FIELDS_MASK));
			}
			return new FileUpdateContext(this);
		}

		public FileUpdateContext.Builder setCurrentSize(long currentSize) {
			this.currentSize = currentSize;
			mask |= SIZE_MASK;
			return this;
		}

		public FileUpdateContext.Builder setCurrentExpiry(long currentExpiry) {
			this.currentExpiry = currentExpiry;
			mask |= EXPIRY_MASK;
			return this;
		}

		public FileUpdateContext.Builder setCurrentMemo(String currentMemo) {
			this.currentMemo = currentMemo;
			mask |= MEMO_MASK;
			return this;
		}

		public FileUpdateContext.Builder setCurrentWacl(KeyList currentWacl) {
			this.currentWacl = currentWacl;
			mask |= WACL_MASK;
			return this;
		}
	}
}
