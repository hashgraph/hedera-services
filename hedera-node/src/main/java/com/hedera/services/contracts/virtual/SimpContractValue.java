package com.hedera.services.contracts.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class SimpContractValue implements VirtualValue {
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x34beec0816955bcbL;
	static final int RELEASE_0200_VERSION = 1;

	public static final int SIZE = 32;

	private byte[] value;

	private boolean immutable = false;

	public SimpContractValue() {
		this.value = new byte[SIZE];
	}

	public SimpContractValue(byte[] value) {
		Objects.requireNonNull(value);
		if (value.length != SIZE) {
			throw new IllegalArgumentException("invalid bytes length");
		}
		this.value = value;
	}

	public byte[] getValue() {
		return this.value;
	}

	public void setValue(byte[] value) {
		this.value = value;
	}

	@Override
	public SimpContractValue copy() {
		return new SimpContractValue(this.value);
	}

	@Override
	public VirtualValue asReadOnly() {
		SimpContractValue copy = copy();
		copy.immutable = true;
		return copy;
	}

	@Override
	public void release() {
	}

	@Override
	public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
		if (immutable) {
			throw new IllegalStateException("cannot set to immutable");
		}
		serializableDataInputStream.read(this.value);
	}

	@Override
	public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
		serializableDataOutputStream.write(this.value);
	}

	@Override
	public void serialize(ByteBuffer byteBuffer) throws IOException {
		byteBuffer.put(this.value);
	}

	@Override
	public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
		if (immutable) {
			throw new IllegalStateException("cannot set to immutable");
		}
		byteBuffer.get(this.value);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return RELEASE_0200_VERSION;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SimpContractValue simpContractValue = (SimpContractValue) o;
		return Arrays.equals(value, simpContractValue.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(value);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Uint256Value{");
		for (int i = 0; i < SIZE; i++) {
			sb.append(String.format("%02X ", this.value[i]).toUpperCase());
		}
		sb.append("}");
		return sb.toString();
	}
}
