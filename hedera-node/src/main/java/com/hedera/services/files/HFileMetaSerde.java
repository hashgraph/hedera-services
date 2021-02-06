package com.hedera.services.files;

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

import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.legacy.core.jproto.JKeySerializer.StreamConsumer;
import com.hedera.services.legacy.core.jproto.JObjectType;
import com.hedera.services.state.serdes.DomainSerdes;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

public class HFileMetaSerde {
	public static final int MAX_CONCEIVABLE_MEMO_UTF8_BYTES = 1_024;
	public static final long PRE_MEMO_VERSION = 1;
	public static final long MEMO_VERSION = 2;

	@FunctionalInterface
	public interface StreamContentDiscovery {
		byte[] discoverFor(StreamConsumer<DataOutputStream> streamConsumer) throws IOException;
	}

	public static DomainSerdes serdes = new DomainSerdes();
	public static StreamContentDiscovery streamContentDiscovery = JKeySerializer::byteStream;
	public static Function<InputStream, SerializableDataInputStream> serInFactory = SerializableDataInputStream::new;
	public static Function<OutputStream, SerializableDataOutputStream> serOutFactory = SerializableDataOutputStream::new;

	public static byte[] serialize(HFileMeta meta) throws IOException {
		return streamContentDiscovery.discoverFor(out -> {
			var serOut = serOutFactory.apply(out);
			serOut.writeLong(MEMO_VERSION);
			serOut.writeBoolean(meta.isDeleted());
			serOut.writeLong(meta.getExpiry());
			serOut.writeNormalisedString(meta.getMemo());
			serdes.writeNullable(meta.getWacl(), serOut, serdes::serializeKey);
		});
	}

	public static HFileMeta deserialize(DataInputStream in) throws IOException {
		long version = in.readLong();
		if (version == PRE_MEMO_VERSION) {
			return readPreMemoMeta(in);
		} else {
			return readMemoMeta(in);
		}
	}

	private static HFileMeta readMemoMeta(DataInputStream in) throws IOException {
		var serIn = serInFactory.apply(in);
		var isDeleted = serIn.readBoolean();
		var expiry = serIn.readLong();
		var memo = serIn.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES);
		var wacl = serdes.readNullable(serIn, serdes::deserializeKey);
		return new HFileMeta(isDeleted, wacl, expiry, memo);
	}

	private static HFileMeta readPreMemoMeta(DataInputStream in) throws IOException {
		long objectType = in.readLong();
		if (objectType != JObjectType.JFileInfo.longValue()) {
			throw new IllegalStateException(String.format("Read illegal object type '%d'!", objectType));
		}
		/* Unused legacy length information. */
		in.readLong();
		return unpack(in);
	}

	private static HFileMeta unpack(DataInputStream stream) throws IOException {
		boolean deleted = stream.readBoolean();
		long expirationTime = stream.readLong();
		byte[] key = stream.readAllBytes();
		JKey wacl = JKeySerializer.deserialize(new DataInputStream(new ByteArrayInputStream(key)));
		return new HFileMeta(deleted, wacl, expirationTime);
	}
}
