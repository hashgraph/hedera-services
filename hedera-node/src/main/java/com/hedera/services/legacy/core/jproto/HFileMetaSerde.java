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

import com.hedera.services.legacy.core.jproto.JKeySerializer.StreamConsumer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class HFileMetaSerde {
	private static final long PRE_MEMO_VERSION = 1;
	private static final long MEMO_VERSION = 2;

	@FunctionalInterface
	interface StreamContentDiscovery {
		byte[] discoverFor(StreamConsumer<DataOutputStream> streamConsumer) throws IOException;
	}

	static StreamContentDiscovery streamContentDiscovery = JKeySerializer::byteStream;

	public static byte[] serialize(HFileMeta fileInfObject) throws IOException {
		return streamContentDiscovery.discoverFor(buffer -> {
			buffer.writeLong(PRE_MEMO_VERSION);
			buffer.writeLong(JObjectType.JFileInfo.longValue());

			byte[] content = streamContentDiscovery.discoverFor(os -> pack(os, fileInfObject));
			int length = (content != null) ? content.length : 0;

			buffer.writeLong(length);

			if (length > 0) {
				buffer.write(content);
			}
		});
	}

	public static HFileMeta deserialize(DataInputStream stream) throws IOException {
		long version;
		version = stream.readLong();
		long objectType = stream.readLong();

		stream.readLong();

		if (objectType != JObjectType.JFileInfo.longValue()) {
			throw new IllegalStateException(String.format("Read illegal object type '%d'!", objectType));
		} else if (version != PRE_MEMO_VERSION) {
			throw new IllegalStateException(String.format("Read unknown version '%d'!", version));
		}

		return unpack(stream);
	}

	private static void pack(DataOutputStream stream, HFileMeta jfi) throws IOException {
		stream.writeBoolean(jfi.isDeleted());
		stream.writeLong(jfi.getExpiry());
		stream.write(JKeySerializer.serialize(jfi.getWacl()));
	}

	private static HFileMeta unpack(DataInputStream stream) throws IOException {
		boolean deleted = stream.readBoolean();
		long expirationTime = stream.readLong();
		byte[] key = stream.readAllBytes();
		JKey wacl = JKeySerializer.deserialize(new DataInputStream(new ByteArrayInputStream(key)));
		return new HFileMeta(deleted, wacl, expirationTime);
	}
}
