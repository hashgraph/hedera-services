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
import com.swirlds.virtualmap.VirtualKey;
import org.hyperledger.besu.datatypes.Address;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class SimpContractKey implements VirtualKey {
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xbd02f789da2d61a5L;
	static final int RELEASE_0200_VERSION = 1;

	private static final int KEY_SIZE = 32;
	private static final int SIZE = Address.SIZE + KEY_SIZE;

	private byte[] contract;

	private byte[] key;

	public SimpContractKey() {
		this.contract = new byte[Address.SIZE];
		this.key = new byte[KEY_SIZE];
	}

	public SimpContractKey(byte[] contract, byte[] key) {
		this.setContract(contract);
		this.setKey(key);
	}

	private void setContract(byte[] contract) {
		Objects.requireNonNull(contract);
		if (contract.length != Address.SIZE) {
			throw new IllegalArgumentException("invalid contract address length");
		}
		this.contract = contract;
	}

	private void setKey(byte[] key) {
		Objects.requireNonNull(key);
		if (key.length != KEY_SIZE) {
			throw new IllegalArgumentException("invalid key length");
		}
		this.key = key;
	}

	@Override
	public void serialize(ByteBuffer byteBuffer) throws IOException {
		byteBuffer.put(contract);
		byteBuffer.put(key);
	}

	@Override
	public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
		byteBuffer.get(contract);
		byteBuffer.get(key);
	}

	@Override
	public boolean equals(ByteBuffer byteBuffer, int i) throws IOException {
		byte[] contract = new byte[Address.SIZE];
		byteBuffer.get(contract);
		byte[] key = new byte[KEY_SIZE];
		byteBuffer.get(key);

		return Arrays.equals(this.contract, contract) && Arrays.equals(this.key, key);
	}

	@Override
	public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
		serializableDataInputStream.read(contract);
		serializableDataInputStream.read(contract);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
		serializableDataOutputStream.write(contract);
		serializableDataOutputStream.write(key);
	}

	@Override
	public int getVersion() {
		return RELEASE_0200_VERSION;
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(this.contract), Arrays.hashCode(this.key));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SimpContractKey that = (SimpContractKey) o;
		return Arrays.equals(this.contract, that.contract) && Arrays.equals(this.key, that.key);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Uint256Key{");
		sb.append("contract:(");
		for (int i = 0; i < 20; i++) {
			sb.append(String.format("%02X ", this.contract[i]).toUpperCase());
		}
		sb.append("),");
		sb.append("key:(");
		for (int i = 0; i < 32; i++) {
			sb.append(String.format("%02X ", this.key[i]).toUpperCase());
		}
		sb.append(")}");

		return sb.toString();
	}
}
