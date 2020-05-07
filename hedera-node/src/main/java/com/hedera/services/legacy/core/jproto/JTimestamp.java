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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is custom class equivalent to Timestamp proto
 *
 * @author Akshay
 * @Date : 1/9/2019
 */
public class JTimestamp implements FastCopyable {

	private static final Logger log = LogManager.getLogger(JTimestamp.class);
	private static final long LEGACY_VERSION_1 = 1;
	private static final long CURRENT_VERSION = 2;
	private long seconds;
	private int nano;

	public JTimestamp() {
	}

	public JTimestamp(long seconds, int nano) {
		this.seconds = seconds;
		this.nano = nano;
	}

	public JTimestamp(final JTimestamp other) {
		this.seconds = other.seconds;
		this.nano = other.nano;
	}

	public long getSeconds() {
		return seconds;
	}

	public void setSeconds(long seconds) {
		this.seconds = seconds;
	}

	public int getNano() {
		return nano;
	}

	public void setNano(int nano) {
		this.nano = nano;
	}

	@Override
	public String toString() {
		return "JTimestamp{" +
				"seconds=" + seconds +
				", nano=" + nano +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JTimestamp that = (JTimestamp) o;
		return seconds == that.seconds &&
				nano == that.nano;
	}

	@Override
	public int hashCode() {
		return Objects.hash(seconds, nano);
	}

	public static JTimestamp convert(Timestamp timestamp) {
		long seconds = timestamp.getSeconds();
		int nanos = timestamp.getNanos();
		if (seconds == 0 && nanos == 0) {
			return null;
		}
		return new JTimestamp(seconds, nanos);
	}

	public static Timestamp convert(JTimestamp startTime) {
		if (startTime == null) {
			return null;
		}
		return Timestamp.newBuilder().setSeconds(startTime.getSeconds())
				.setNanos(startTime.getNano()).build();
	}

	/**
	 * Custom serialize method. If some field is null then it will set 0 byte for that field
	 * otherwise it add length of the byte first and then actual byte of the field.
	 */

	private void serialize(final DataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(JObjectType.JTimestamp.longValue());
		outStream.writeLong(this.seconds);
		outStream.writeInt(this.nano);
	}

	/**
	 * Custom deserialization  of this class. It read first length of the field if it is 0 then
	 * sets field to null otherwise it read bytes from DataInputStream of specified length and
	 * deserialize those byte for the field.
	 *
	 * @return deserialize JTimestamp
	 */
	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		JTimestamp jTimestamp = new JTimestamp();

		deserialize((FCDataInputStream)inStream, jTimestamp);
		return (T) jTimestamp;
	}

	private static void deserialize(final FCDataInputStream inStream, final JTimestamp jTimestamp) throws IOException {
		long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		long objectType = inStream.readLong();
		JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JTimestamp.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		jTimestamp.seconds = inStream.readLong();
		jTimestamp.nano = inStream.readInt();
	}

	@Override
	public FastCopyable copy() {
		return new JTimestamp(this);
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

	public boolean isAfter(JTimestamp other) {
		return (seconds > other.seconds) ||
				((seconds == other.seconds) && (nano > other.nano));
	}
}
