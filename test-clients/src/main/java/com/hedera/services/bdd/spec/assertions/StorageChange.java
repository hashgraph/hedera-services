package com.hedera.services.bdd.spec.assertions;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;

public class StorageChange {
	private ByteString slot;
	private ByteString valueRead;
	private BytesValue valueWritten;

	private StorageChange(ByteString slot, ByteString value) {
		this.slot = slot;
		this.valueRead = value;
		this.valueWritten = BytesValue.getDefaultInstance();
	}

	private StorageChange(ByteString slot, ByteString prevValue, BytesValue value) {
		this.slot = slot;
		this.valueRead = prevValue;
		this.valueWritten = value;
	}

	public static StorageChange onlyRead(ByteString slot, ByteString value) {
		return new StorageChange(slot, value);
	}

	public static StorageChange readAndWritten(ByteString slot, ByteString prevValue, ByteString value) {
		return new StorageChange(slot, prevValue, BytesValue.of(value));
	}

	public ByteString getSlot() {
		return this.slot;
	}

	public ByteString getValueRead() {
		return this.valueRead;
	}

	public BytesValue getValueWritten() {
		return this.valueWritten;
	}
}
