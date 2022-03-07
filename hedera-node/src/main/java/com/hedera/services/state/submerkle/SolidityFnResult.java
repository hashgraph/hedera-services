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
import com.google.protobuf.BytesValue;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.utils.BytesComparator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractStateChange;
import com.hederahashgraph.api.proto.java.StorageChange;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.swirlds.common.CommonUtils.hex;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class SolidityFnResult implements SelfSerializable {
	private static final byte[] MISSING_BYTES = new byte[0];

	static final int MERKLE_VERSION = 2;

	static final int PRE_RELEASE_0230_VERSION = 1;
	static final int RELEASE_0230_VERSION = 2;
	static final int RELEASE_0240_VERSION = 3;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x2055c5c03ff84eb4L;

	static DomainSerdes serdes = new DomainSerdes();

	public static final int MAX_LOGS = 1_024;
	public static final int MAX_CREATED_IDS = 32;
	public static final int MAX_ERROR_BYTES = Integer.MAX_VALUE;
	public static final int MAX_RESULT_BYTES = Integer.MAX_VALUE;
	public static final int MAX_ADDRESS_BYTES = 20;

	private long gasUsed;
	private byte[] bloom = MISSING_BYTES;
	private byte[] result = MISSING_BYTES;
	private byte[] evmAddress = MISSING_BYTES;
	private String error;
	private EntityId contractId;
	private List<EntityId> createdContractIds = new ArrayList<>();
	private List<SolidityLog> logs = new ArrayList<>();
	private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = new TreeMap<>();

	public SolidityFnResult() {
		/* RuntimeConstructable */
	}

	public SolidityFnResult(
			EntityId contractId,
			byte[] result,
			String error,
			byte[] bloom,
			long gasUsed,
			List<SolidityLog> logs,
			List<EntityId> createdContractIds,
			byte[] evmAddress,
			Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges
	) {
		this.contractId = contractId;
		this.result = result;
		this.error = error;
		this.bloom = bloom;
		this.gasUsed = gasUsed;
		this.logs = logs;
		this.createdContractIds = createdContractIds;
		this.evmAddress = evmAddress;
		this.stateChanges = stateChanges;
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
		if (version >= RELEASE_0230_VERSION) {
			evmAddress = in.readByteArray(MAX_ADDRESS_BYTES);
		}
		if (version >= RELEASE_0240_VERSION) {
			int numAffectedContracts = in.readInt();
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> state = new TreeMap<>();
			while (numAffectedContracts-- > 0) {
				final byte[] contractAddress = in.readByteArray(MAX_ADDRESS_BYTES);
				int numAffectedSlots = in.readInt();
				final Map<Bytes, Pair<Bytes, Bytes>> storage = new TreeMap<>();
				state.put(Address.fromHexString(hex(contractAddress)), storage);
				while (numAffectedSlots-- > 0) {
					Bytes slot = Bytes.wrap(in.readByteArray(32));
					Bytes left = Bytes.wrap(in.readByteArray(32));
					boolean hasRight = in.readBoolean();
					Bytes right = hasRight ? Bytes.wrap(in.readByteArray(32)) : null;
					storage.put(slot, Pair.of(left, right));
				}
			}
			stateChanges = state;
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(gasUsed);
		out.writeByteArray(bloom);
		out.writeByteArray(result);
		serdes.writeNullableString(error, out);
		serdes.writeNullableSerializable(contractId, out);
		out.writeSerializableList(logs, true, true);
		out.writeSerializableList(createdContractIds, true, true);
		out.writeByteArray(evmAddress);
		out.writeInt(stateChanges.size());
		for (Map.Entry<Address, Map<Bytes, Pair<Bytes, Bytes>>> entry : stateChanges.entrySet()) {
			out.writeByteArray(entry.getKey().trimLeadingZeros().toArrayUnsafe());
			Map<Bytes, Pair<Bytes, Bytes>> slots = entry.getValue();
			out.writeInt(slots.size());
			for (var slot : slots.entrySet()) {
				out.writeByteArray(slot.getKey().trimLeadingZeros().toArrayUnsafe());
				out.writeByteArray(slot.getValue().getLeft().trimLeadingZeros().toArrayUnsafe());
				Bytes right = slot.getValue().getRight();
				if (right == null) {
					out.writeBoolean(false);
				} else {
					out.writeBoolean(true);
					out.writeByteArray(right.trimLeadingZeros().toArrayUnsafe());
				}
			}
		}
	}

	/* --- Object --- */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || SolidityFnResult.class != o.getClass()) {
			return false;
		}
		var that = (SolidityFnResult) o;
		return gasUsed == that.gasUsed &&
				Objects.equals(contractId, that.contractId) &&
				Arrays.equals(result, that.result) &&
				Objects.equals(error, that.error) &&
				Arrays.equals(bloom, that.bloom) &&
				Objects.equals(logs, that.logs) &&
				Objects.equals(createdContractIds, that.createdContractIds) &&
				Arrays.equals(evmAddress, that.evmAddress) &&
				this.stateChanges.equals(that.stateChanges);
	}

	@Override
	public int hashCode() {
		var code = Objects.hash(contractId, error, gasUsed, logs, createdContractIds);
		code = code * 31 + Arrays.hashCode(result);
		code = code * 31 + Arrays.hashCode(bloom);
		return code * 31 + Arrays.hashCode(evmAddress);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("gasUsed", gasUsed)
				.add("bloom", hex(bloom))
				.add("result", hex(result))
				.add("error", error)
				.add("contractId", contractId)
				.add("createdContractIds", createdContractIds)
				.add("logs", logs)
				.add("stateChanges", stateChanges)
				.add("evmAddress", hex(evmAddress))
				.toString();
	}

	/* --- Bean --- */
	public void setContractId(final EntityId contractId) {
		this.contractId = contractId;
	}

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

	public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getStateChanges() {
		return stateChanges;
	}

	public byte[] getEvmAddress() {
		return evmAddress;
	}

	public void setEvmAddress(final byte[] evmAddress) {
		this.evmAddress = evmAddress;
	}

	public void setStateChanges(final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
		this.stateChanges = stateChanges;
	}

	/* --- Helpers --- */
	public static SolidityFnResult fromGrpc(final ContractFunctionResult that) {
		return new SolidityFnResult(
				that.hasContractID() ? EntityId.fromGrpcContractId(that.getContractID()) : null,
				that.getContractCallResult().isEmpty() ? MISSING_BYTES : that.getContractCallResult().toByteArray(),
				!that.getContractCallResult().isEmpty() ? that.getErrorMessage() : null,
				that.getBloom().isEmpty() ? MISSING_BYTES : that.getBloom().toByteArray(),
				that.getGasUsed(),
				that.getLogInfoList().stream().map(SolidityLog::fromGrpc).toList(),
				that.getCreatedContractIDsList().stream().map(EntityId::fromGrpcContractId).toList(),
				that.hasEvmAddress() ? that.getEvmAddress().getValue().toByteArray() : MISSING_BYTES,
				that.getStateChangesList().stream().collect(Collectors.toMap(
						csc -> Address.wrap(Bytes.wrap(asEvmAddress(csc.getContractID()))),
						csc -> csc.getStorageChangesList().stream().collect(Collectors.toMap(
								sc -> Bytes.wrap(sc.getSlot().toByteArray()).trimLeadingZeros(),
								sc -> Pair.of(
										Bytes.wrap(sc.getValueRead().toByteArray()).trimLeadingZeros(),
										!sc.hasValueWritten() ? null :
												Bytes.wrap(sc.getValueWritten().getValue().toByteArray()).trimLeadingZeros()),
								(l, r) -> l,
								() -> new TreeMap<>(BytesComparator.INSTANCE)
						)),
						(l, r) -> l,
						() -> new TreeMap<>(BytesComparator.INSTANCE)))
		);
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
			grpc.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).toList());
		}
		if (isNotEmpty(createdContractIds)) {
			grpc.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).toList());
		}
		grpc.setEvmAddress(BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)));
		for (var changes : stateChanges.entrySet()) {
			var contractStateChange = ContractStateChange.newBuilder().setContractID(
					EntityIdUtils.contractIdFromEvmAddress(changes.getKey().toArrayUnsafe()));
			for (var slotChange : changes.getValue().entrySet()) {
				var storageChange = StorageChange.newBuilder();
				storageChange.setSlot(ByteString.copyFrom(slotChange.getKey().toArrayUnsafe()));
				Pair<Bytes, Bytes> value = slotChange.getValue();
				storageChange.setValueRead(ByteString.copyFrom(value.getLeft().toArrayUnsafe()));
				Bytes valueRight = value.getRight();
				if (valueRight != null) {
					storageChange.setValueWritten(BytesValue.newBuilder().setValue(ByteString.copyFrom(valueRight.toArrayUnsafe())).build());
				}
				contractStateChange.addStorageChanges(storageChange.build());
			}
			grpc.addStateChanges(contractStateChange);
		}
		return grpc.build();
	}
}
