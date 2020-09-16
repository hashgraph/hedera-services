package com.hedera.services.state.submerkle;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class SolidityLog implements SelfSerializable {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x2af05aa9c7ff917L;

	private static final byte[] MISSING_BYTES = new byte[0];

	private byte[] data = MISSING_BYTES;
	private byte[] bloom = MISSING_BYTES;
	private EntityId contractId;
	private List<byte[]> topics = Collections.emptyList();

	static DomainSerdes serdes = new DomainSerdes();
	static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;

	public static final int MAX_DATA_BYTES = 32 * 1024;
	public static final int MAX_BLOOM_BYTES = 256;
	public static final int MAX_TOPIC_BYTES = 1024;

	public static final Provider LEGACY_PROVIDER = new Provider();

	@Deprecated
	public static class Provider {
		public SolidityLog deserialize(DataInputStream in) throws IOException {
			var log = new SolidityLog();

			in.readLong();
			in.readLong();
			if (in.readBoolean()) {
				log.contractId = legacyIdProvider.deserialize(in);
			}

			int numBloomBytes = in.readInt();
			if (numBloomBytes > 0) {
				log.bloom = new byte[numBloomBytes];
				in.readFully(log.bloom);
			}

			int numDataBytes = in.readInt();
			if (numDataBytes > 0) {
				log.data = new byte[numDataBytes];
				in.readFully(log.data);
			}

			int numTopics = in.readInt();
			if (numTopics > 0) {
				log.topics = new LinkedList<>();
				for (int i = 0; i < numTopics; i++) {
					int numTopicBytes = in.readInt();
					if (numTopicBytes > 0) {
						byte[] topic = new byte[numTopicBytes];
						in.readFully(topic);
						log.topics.add(topic);
					} else {
						log.topics.add(MISSING_BYTES);
					}
				}
			}
			return log;
		}
	}

	public SolidityLog() {
	}

	public SolidityLog(
			EntityId contractId,
			byte[] bloom,
			List<byte[]> topics,
			byte[] data
	) {
		this.contractId = contractId;
		this.bloom = bloom;
		this.topics = topics;
		this.data = data;
	}

	/* --- SelfSerializable --- */

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		data = in.readByteArray(MAX_DATA_BYTES);
		bloom = in.readByteArray(MAX_BLOOM_BYTES);
		contractId = serdes.readNullableSerializable(in);
		int numTopics = in.readInt();
		if (numTopics > 0) {
			topics = new LinkedList<>();
			for (int i = 0; i < numTopics; i++) {
				topics.add(in.readByteArray(MAX_TOPIC_BYTES));
			}
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeByteArray(data);
		out.writeByteArray(bloom);
		serdes.writeNullableSerializable(contractId, out);
		out.writeInt(topics.size());
		for (byte[] topic : topics) {
			out.writeByteArray(topic);
		}
	}

	/* --- Object --- */

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || SolidityLog.class != o.getClass()) {
			return false;
		}
		SolidityLog that = (SolidityLog) o;
		return Objects.equals(contractId, that.contractId) &&
				Arrays.equals(bloom, that.bloom) &&
				Arrays.equals(data, that.data) &&
				areSameTopics(topics, that.topics);
	}

	private static boolean areSameTopics(List<byte[]> a, List<byte[]> b) {
		int aLen = Optional.ofNullable(a).map(List::size).orElse(-1);
		int bLen = Optional.ofNullable(b).map(List::size).orElse(-1);
		if (aLen != bLen) {
			return false;
		} else {
			for (int i = 0; i < aLen; i++) {
				if (!Arrays.equals(a.get(i), b.get(i))) {
					return false;
				}
			}
			return true;
		}
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(contractId, topics);
		result = 31 * result + Arrays.hashCode(bloom);
		return 31 * result + Arrays.hashCode(data);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("data", Hex.encodeHexString(data))
				.add("bloom", Hex.encodeHexString(bloom))
				.add("contractId", contractId)
				.add("topics", topics.stream().map(Hex::encodeHexString).collect(toList()))
				.toString();
	}

	/* --- Bean --- */

	public EntityId getContractId() {
		return contractId;
	}

	public byte[] getBloom() {
		return bloom;
	}

	public List<byte[]> getTopics() {
		return topics;
	}

	public byte[] getData() {
		return data;
	}

	/* --- Helpers --- */

	public static SolidityLog fromGrpc(ContractLoginfo grpc) {
		return new SolidityLog(
				EntityId.ofNullableContractId(grpc.hasContractID() ? grpc.getContractID() : null),
				grpc.getBloom().isEmpty() ? MISSING_BYTES : grpc.getBloom().toByteArray(),
				grpc.getTopicList().stream().map(ByteString::toByteArray).collect(toList()),
				grpc.getData().isEmpty() ? MISSING_BYTES : grpc.getData().toByteArray());
	}

	public ContractLoginfo toGrpc() {
		var grpc = ContractLoginfo.newBuilder();
		if (contractId != null) {
			grpc.setContractID(contractId.toGrpcContractId());
		}
		grpc.setBloom(ByteString.copyFrom(bloom));
		grpc.setData(ByteString.copyFrom(data));
		grpc.addAllTopic(topics.stream().map(ByteString::copyFrom).collect(toList()));
		return grpc.build();
	}
}
