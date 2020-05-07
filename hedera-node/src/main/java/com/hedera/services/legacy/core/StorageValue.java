package com.hedera.services.legacy.core;

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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.blob.BinaryObject;
import com.swirlds.blob.BinaryObjectStore;

/**
 * This storage details will be stored in FCM which in memory map.
 *
 * @author plynn
 * @Date : 4/26/2019
 */
public class StorageValue implements FastCopyable {

	private static final long LEGACY_VERSION = 1;
    private static final long CURRENT_VERSION = 2;
	private static final long OBJECT_ID = 15487003;
	private BinaryObject data;

	public StorageValue() {
	}

	public StorageValue(final byte[] data) {
		this.data = BinaryObjectStore.getInstance().put(data);
	}

	public StorageValue(final StorageValue other) {
		this.data = other.data.copy();
	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		StorageValue val = new StorageValue();

		deserialize(inStream, val);
		return (T) val;
	}

	private static void deserialize(final DataInputStream inStream, final StorageValue val) throws IOException {
		long version = inStream.readLong();
		inStream.readLong();

		if(version == LEGACY_VERSION) {
		    int length = inStream.readInt();
			if (length > 0) {
				byte[] newData = new byte[length];
				inStream.readFully(newData);
				val.data = BinaryObjectStore.getInstance().put(newData);
			}
		} else {
	      final boolean hasData = inStream.readBoolean();
          if (hasData) {
            val.data = new BinaryObject();
            val.data.copyFrom((FCDataInputStream)inStream);
            val.data.copyFromExtra((FCDataInputStream)inStream);
          }
		}
	}

	public byte[] getData() {
		if (data == null) {
			return new byte[0];
		}

		return BinaryObjectStore.getInstance().get(data);
	}

	public void setData(final byte[] data) {
		if (data != null) {
			this.data = BinaryObjectStore.getInstance().update(this.data, data);
		} else {
			this.data = BinaryObjectStore.getInstance().put(data);
		}
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		StorageValue storageValue = (StorageValue) o;
		return Objects.equals(data, storageValue.data);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Objects.hashCode(data));
	}

	@Override
	public String toString() {
		return "StorageValue{" + "data=" + data + '}';
	}

	/**
	 * DO NO DISTURB THE SERIALIZATION AND DESERIALIZATION ORDER
	 */

	private void serialize(final DataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(OBJECT_ID);

		if (data != null) {
			outStream.writeBoolean(true);
			data.copyTo((FCDataOutputStream) outStream);
			data.copyToExtra((FCDataOutputStream) outStream);
		} else {
			outStream.writeBoolean(false);
		}
	}

	public BinaryObject getBinaryObject() {
		return data;
	}

	@Override
	public FastCopyable copy() {
		return new StorageValue(this);
	}

	@Override
	public void copyTo(final FCDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final FCDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyToExtra(final FCDataOutputStream outStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyFromExtra(final FCDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream)
			throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream)
			throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {
		if (data != null) {
			data.delete();
		}
	}
}
