package com.hedera.services.state.submerkle;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class SolidityFnResult implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(SolidityFnResult.class);

	private static final byte[] MISSING_BYTES = new byte[0];

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x2055c5c03ff84eb4L;

	static DomainSerdes serdes = new DomainSerdes();
	static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;
	static SolidityLog.Provider legacyLogProvider = SolidityLog.LEGACY_PROVIDER;

	public static final int MAX_LOGS = 1_024;
	public static final int MAX_CREATED_IDS = 32;
	public static final int MAX_ERROR_BYTES = 1_024;
	public static final int MAX_RESULT_BYTES = 1_024 * 1_024;
	public static final Provider LEGACY_PROVIDER = new Provider();

	private long gasUsed;
	private byte[] bloom = MISSING_BYTES;
	private byte[] result = MISSING_BYTES;
	private String error;
	private EntityId contractId;
	private List<EntityId> createdContractIds = new ArrayList<>();
	private List<SolidityLog> logs = new ArrayList<>();

	@Deprecated
	public static class Provider {
		private static final long VERSION_WITH_CREATED_IDS = 3L;

		public SolidityFnResult deserialize(DataInputStream in) throws IOException {
			var fnResult = new SolidityFnResult();

			var version = in.readLong();
			in.readLong();

			if (in.readBoolean()) {
				fnResult.contractId = legacyIdProvider.deserialize(in);
			}

			int numResultBytes = in.readInt();
			if (numResultBytes > 0) {
				fnResult.result = new byte[numResultBytes];
				in.readFully(fnResult.result);
			}

			int numErrorBytes = in.readInt();
			if (numErrorBytes > 0) {
				byte[] errorBytes = new byte[numErrorBytes];
				in.readFully(errorBytes);
				fnResult.error = StringUtils.newStringUtf8(errorBytes);
			}

			int numBloomBytes = in.readInt();
			if (numBloomBytes > 0) {
				fnResult.bloom = new byte[numBloomBytes];
				in.readFully(fnResult.bloom);
			}

			fnResult.gasUsed = in.readLong();

			int numLogs = in.readInt();
			for (int i = 0; i < numLogs; i++) {
				fnResult.logs.add(legacyLogProvider.deserialize(in));
			}

			if (version == VERSION_WITH_CREATED_IDS) {
				int numCreatedContracts = in.readInt();
				for (int i = 0; i < numCreatedContracts; i++) {
					fnResult.createdContractIds.add(legacyIdProvider.deserialize(in));
				}
			}

			return fnResult;
		}
	}

	public SolidityFnResult() { }

	public SolidityFnResult(
			EntityId contractId,
			byte[] result,
			String error,
			byte[] bloom,
			long gasUsed,
			List<SolidityLog> logs,
			List<EntityId> createdContractIds
	) {
		this.contractId = contractId;
		this.result = result;
		this.error = error;
		this.bloom = bloom;
		this.gasUsed = gasUsed;
		this.logs = logs;
		this.createdContractIds = createdContractIds;
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
		gasUsed = in.readLong();
		bloom = in.readByteArray(SolidityLog.MAX_BLOOM_BYTES);
		result = in.readByteArray(MAX_RESULT_BYTES);
		error = serdes.readNullableString(in, MAX_ERROR_BYTES);
		contractId = serdes.readNullableSerializable(in);
		logs = in.readSerializableList(MAX_LOGS, true, SolidityLog::new);
		createdContractIds = in.readSerializableList(MAX_CREATED_IDS, true, EntityId::new);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(gasUsed);
		out.writeByteArray(bloom);
		out.writeByteArray(result);
		serdes.writeNullableString(error, out);
		serdes.writeNullableSerializable(contractId, out);
		out.writeSerializableList(logs, true, true);
		out.writeSerializableList(createdContractIds, true, true);
	}

	/* --- Object --- */

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || SolidityFnResult.class != o.getClass()) {
			return false;
		}
		var that = (SolidityFnResult)o;
		return gasUsed == that.gasUsed &&
				Objects.equals(contractId, that.contractId) &&
				Arrays.equals(result, that.result) &&
				Objects.equals(error, that.error) &&
				Arrays.equals(bloom, that.bloom) &&
				Objects.equals(logs, that.logs) &&
				Objects.equals(createdContractIds, that.createdContractIds);
	}

	@Override
	public int hashCode() {
		var code = Objects.hash(contractId, error, gasUsed, logs, createdContractIds);
		code = code * 31 + Arrays.hashCode(result);
		return code * 31 + Arrays.hashCode(bloom);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("gasUsed", gasUsed)
				.add("bloom", Hex.encodeHexString(bloom))
				.add("result", Hex.encodeHexString(result))
				.add("error", error)
				.add("contractId", contractId)
				.add("createdContractIds", createdContractIds)
				.add("logs", logs)
				.toString();
	}

	/* --- Bean --- */

	public EntityId getContractId() {
		return contractId;
	}

	public byte[] getResult() {
		return result;
	}

	public String getError() {
		return error;
	}

	public byte[] getBloom() {
		return bloom;
	}

	public long getGasUsed() {
		return gasUsed;
	}

	public List<SolidityLog> getLogs() {
		return logs;
	}

	public List<EntityId> getCreatedContractIds() {
		return createdContractIds;
	}

	/* --- Helpers --- */

	public static SolidityFnResult fromGrpc(ContractFunctionResult that) {
		return new SolidityFnResult(
				EntityId.ofNullableContractId(that.hasContractID() ? that.getContractID() : null),
				that.getContractCallResult().isEmpty() ? MISSING_BYTES : that.getContractCallResult().toByteArray(),
				!that.getContractCallResult().isEmpty() ? that.getErrorMessage() : null,
				that.getBloom().isEmpty() ? MISSING_BYTES : that.getBloom().toByteArray(),
				that.getGasUsed(),
				that.getLogInfoList().stream().map(SolidityLog::fromGrpc).collect(toList()),
				that.getCreatedContractIDsList().stream().map(EntityId::ofNullableContractId).collect(toList()));
	}

	public ContractFunctionResult toGrpc() {
		var grpc = ContractFunctionResult.newBuilder();
		grpc.setGasUsed(gasUsed);
		grpc.setBloom(ByteString.copyFrom(bloom));
		grpc.setContractCallResult(ByteString.copyFrom(result));
		if (error != null) {
			grpc.setErrorMessage(error);
		}
		if (contractId != null) {
			grpc.setContractID(contractId.toGrpcContractId());
		}
		if (isNotEmpty(logs)) {
			grpc.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()));
		}
		if (isNotEmpty(createdContractIds)) {
			grpc.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()));
		}
		return grpc.build();
	}
}
