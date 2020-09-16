package com.hedera.services.legacy.unit.handler;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.ethereum.util.ByteUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * @author Akshay
 * @Date : 9/19/2018
 */
public class SolidityAddress implements FastCopyable {
	private static final long CURRENT_VERSION = 2;
	private static final long OBJECT_ID = 15486041;
	private String solidityAddress;

	public SolidityAddress() {
	}

	public SolidityAddress(final String solidityAddress) {
		this.solidityAddress = solidityAddress;
	}

	public SolidityAddress(final SolidityAddress other) {
		this.solidityAddress = other.solidityAddress;
	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
			throws IOException {
		SolidityAddress value = new SolidityAddress();

		deserialize(inStream, value);
		return (T) value;
	}

	private static void deserialize(final DataInputStream inStream, final SolidityAddress address) throws IOException {
		long version = inStream.readLong();
		long objectId = inStream.readLong();

		byte[] ethAddressByte = new byte[20];
		inStream.readFully(ethAddressByte);
		address.solidityAddress = ByteUtil.toHexString(ethAddressByte);
	}

	@Override
	public FastCopyable copy() {
		return new SolidityAddress(this);
	}

	@Override
	public void copyFrom(final SerializableDataInputStream inStream) throws IOException {

	}

	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) throws IOException {

	}

	@Override
	public void delete() {
	}

	public String getSolidityAddress() {
		return solidityAddress;
	}

	private void serialize(final SerializableDataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(OBJECT_ID);
		outStream.write(ByteUtil.hexStringToBytes(this.solidityAddress));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SolidityAddress that = (SolidityAddress) o;
		return Objects.equals(solidityAddress, that.solidityAddress);
	}

	@Override
	public int hashCode() {
		return Objects.hash(solidityAddress);
	}
}
