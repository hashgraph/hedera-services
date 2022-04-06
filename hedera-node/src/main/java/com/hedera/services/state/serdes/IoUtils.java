package com.hedera.services.state.serdes;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class IoUtils {
	public static <T> T staticReadNullable(
			final SerializableDataInputStream in,
			final IoReadingFunction<T> reader
	) throws IOException {
		return in.readBoolean() ? reader.read(in) : null;
	}

	public static <T extends SelfSerializable> void staticWriteNullableSerializable(
			final T data,
			final SerializableDataOutputStream out
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeSerializable(data, true);
		}
	}

	public static <T extends SelfSerializable> T staticReadNullableSerializable(
			final SerializableDataInputStream in
	) throws IOException {
		return in.readBoolean() ? in.readSerializable() : null;
	}

	public static <T> void staticWriteNullable(
			final T data,
			final SerializableDataOutputStream out,
			final IoWritingConsumer<T> writer
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			writer.write(data, out);
		}
	}

	public static byte[] byteStream(JKeySerializer.StreamConsumer<DataOutputStream> consumer) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			try (DataOutputStream dos = new DataOutputStream(bos)) {
				consumer.accept(dos);
				dos.flush();
				bos.flush();
				return bos.toByteArray();
			}
		}
	}

	public static RichInstant staticReadNullableInstant(SerializableDataInputStream in) throws IOException {
		return staticReadNullable(in, RichInstant::from);
	}
}
