package com.hedera.services.legacy.core.jproto;

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
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;

public class JContractLogInfo implements FastCopyable {
	private static final long LEGACY_VERSION_1 = 1;
	private static final long CURRENT_VERSION = 2;
	private JAccountID contractID;
	private byte[] bloom;
	private byte[] data;
	private List<byte[]> topic;

	public JContractLogInfo() {
		this.topic = new LinkedList<>();
	}

	public JContractLogInfo(final JAccountID contractID, final byte[] bloom, final List<byte[]> topic,
			final byte[] data) {
		this.contractID = contractID;
		this.bloom = bloom;
		this.topic = topic;
		this.data = data;
	}

	public JContractLogInfo(final JContractLogInfo other) {
		this.contractID = (other.contractID != null) ? (JAccountID) other.contractID.copy() : null;
		this.bloom = (other.bloom != null) ? Arrays.copyOf(other.bloom, other.bloom.length) : null;
		this.data = (other.data != null) ? Arrays.copyOf(other.data, other.data.length) : null;
		this.topic = (other.topic != null) ? new LinkedList<>(other.topic) : new LinkedList<>();
	}

	public JAccountID getContractID() {
		return contractID;
	}

	public void setContractID(final JAccountID contractID) {
		this.contractID = contractID;
	}

	public byte[] getBloom() {
		return bloom;
	}

	public void setBloom(final byte[] bloom) {
		this.bloom = bloom;
	}

	public List<byte[]> getTopic() {
		return topic;
	}

	public void setTopic(final List<byte[]> topic) {
		this.topic = topic;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(final byte[] data) {
		this.data = data;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JContractLogInfo that = (JContractLogInfo) o;
		return Objects.equals(contractID, that.contractID) &&
				Arrays.equals(bloom, that.bloom) &&
				Arrays.equals(data, that.data) &&
				Objects.equals(topic, that.topic);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(contractID, topic);
		result = 31 * result + Arrays.hashCode(bloom);
		result = 31 * result + Arrays.hashCode(data);
		return result;
	}

	@Override
	public String toString() {
		return "JContractLogInfo{" +
				"contractID=" + contractID +
				", bloom=" + Arrays.toString(bloom) +
				", data=" + Arrays.toString(data) +
				", topic=" + topic +
				'}';
	}

	/**
	 * Custom serialize method. If some field is null then it will set 0 byte for that field
	 * otherwise it add length of the byte first and then actual byte of the field.
	 *
	 * @return serialized byte array of this class
	 */

	private void serialize(final FCDataOutputStream outStream) throws IOException {

		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(JObjectType.JContractLogInfo.longValue());

		if (this.contractID != null) {
			outStream.writeBoolean(true);
			this.contractID.copyTo(outStream);
			this.contractID.copyToExtra(outStream);
		} else {
			outStream.writeBoolean(false);
		}

		if (this.bloom != null && this.bloom.length > 0) {
			outStream.writeInt(this.bloom.length);
			outStream.write(this.bloom);
		} else {
			outStream.writeInt(0);
		}

		if (this.data != null && this.data.length > 0) {
			outStream.writeInt(this.data.length);
			outStream.write(this.data);
		} else {
			outStream.writeInt(0);
		}

		if (CollectionUtils.isNotEmpty(topic)) {
			outStream.writeInt(topic.size());
			for (byte[] bytes : topic) {
				if (bytes != null && bytes.length > 0) {
					outStream.writeInt(bytes.length);
					outStream.write(bytes);
				} else {
					outStream.writeInt(0);
				}
			}
		} else {
			outStream.writeInt(0);
		}

	}

	/**
	 * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
	 * field to null otherwise it read bytes from DataInputStream of specified length and
	 * deserialize those byte for the field.
	 *
	 * @return deserialize JContractLogInfo
	 */
	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		final JContractLogInfo contractLogInfo = new JContractLogInfo();

		deserialize(inStream, contractLogInfo);
		return (T) contractLogInfo;
	}

	private static void deserialize(final DataInputStream inStream,
			final JContractLogInfo contractLogInfo) throws IOException {
		final long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		final long objectType = inStream.readLong();
		final JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JContractLogInfo.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		boolean contractIDPresent;

		if (version == LEGACY_VERSION_1) {
			contractIDPresent = inStream.readInt() > 0;
		} else {
			contractIDPresent = inStream.readBoolean();
		}

		if (contractIDPresent) {
			contractLogInfo.contractID = JAccountID.deserialize(inStream);
		} else {
			contractLogInfo.contractID = null;
		}

		final byte[] BBytes = new byte[inStream.readInt()];
		if (BBytes.length > 0) {
			inStream.readFully(BBytes);
		}
		contractLogInfo.bloom = BBytes;

		final byte[] DBytes = new byte[inStream.readInt()];
		if (DBytes.length > 0) {
			inStream.readFully(DBytes);
		}
		contractLogInfo.data = DBytes;

		final int listSize = inStream.readInt();
		if (listSize > 0) {
			List<byte[]> topicList = new LinkedList<>();
			for (int i = 0; i < listSize; i++) {
				byte[] TBytes = new byte[inStream.readInt()];

				if (TBytes.length > 0) {
					inStream.readFully(TBytes);
				}

				topicList.add(TBytes);
			}

			contractLogInfo.topic = topicList;
		} else {
			contractLogInfo.topic = new LinkedList<>();
		}
	}

	@Override
	public FastCopyable copy() {
		return new JContractLogInfo(this);
	}

	@Override
	public void copyTo(final FCDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final FCDataInputStream inStream) throws IOException {

	}

	@Override
	public void copyToExtra(final FCDataOutputStream outStream) throws IOException {

	}

	@Override
	public void copyFromExtra(final FCDataInputStream inStream) throws IOException {

	}

	@Override
	public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {

	}
}
