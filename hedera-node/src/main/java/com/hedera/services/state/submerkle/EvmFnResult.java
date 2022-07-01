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
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.readNullableString;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableString;
import static com.swirlds.common.utility.CommonUtils.hex;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class EvmFnResult implements SelfSerializable {
	public static final byte[] EMPTY = new byte[0];

	static final int RELEASE_0240_VERSION = 3;
	static final int RELEASE_0250_VERSION = 4;
	static final int RELEASE_0260_VERSION = 5;
	static final int RELEASE_0280_VERSION = 6;
	static final int CURRENT_VERSION = RELEASE_0280_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0x2055c5c03ff84eb4L;

	public static final int MAX_LOGS = 1_024;
	public static final int MAX_CREATED_IDS = 32;
	public static final int MAX_ERROR_BYTES = Integer.MAX_VALUE;
	public static final int MAX_RESULT_BYTES = Integer.MAX_VALUE;
	public static final int MAX_ADDRESS_BYTES = 20;
	public static final int MAX_FUNCTION_PARAMETERS_BYTES = Integer.MAX_VALUE;

	private long gasUsed;
	private byte[] bloom = EMPTY;
	private byte[] result = EMPTY;
	private byte[] evmAddress = EMPTY;
	private String error;
	private EntityId contractId;
	private List<EntityId> createdContractIds = Collections.emptyList();
	private List<EvmLog> logs = Collections.emptyList();
	private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = Collections.emptyMap();
	private long gas;
	private long amount;
	private byte[] functionParameters = EMPTY;
	private EntityId senderId;

	public EvmFnResult() {
		// RuntimeConstructable
	}

	public static EvmFnResult fromCall(final TransactionProcessingResult result) {
		return from(result, EMPTY);
	}

	public static EvmFnResult fromCreate(final TransactionProcessingResult result, final byte[] evmAddress) {
		return from(result, evmAddress);
	}

	private static EvmFnResult from(final TransactionProcessingResult result, final byte[] evmAddress) {
		if (result.isSuccessful()) {
			final var recipient = result.getRecipient().orElse(Address.ZERO);
			if (Address.ZERO == recipient) {
				throw new IllegalArgumentException("Successful processing result had no recipient");
			}
			return success(
					result.getLogs(),
					result.getGasUsed(),
					result.getOutput(),
					recipient,
					serializableIdsFrom(result.getCreatedContracts()),
					evmAddress);
		} else {
			final var error = result.getRevertReason()
					.map(Object::toString)
					.orElse(result.getHaltReason().map(Object::toString).orElse(null));
			return failure(result.getGasUsed(), error);
		}
	}

	public EvmFnResult(
			final EntityId contractId,
			final byte[] result,
			final String error,
			final byte[] bloom,
			final long gasUsed,
			final List<EvmLog> logs,
			final List<EntityId> createdContractIds,
			final byte[] evmAddress,
			final long gas,
			final long amount,
			final byte[] functionParameters,
			final EntityId senderId
	) {
		this.contractId = contractId;
		this.result = result;
		this.error = error;
		this.bloom = bloom;
		this.gasUsed = gasUsed;
		this.logs = logs;
		this.createdContractIds = createdContractIds;
		this.evmAddress = evmAddress;
		this.gas = gas;
		this.amount = amount;
		this.functionParameters = functionParameters;
		this.senderId = senderId;
	}

//	public EvmFnResult(
//			final EntityId contractId,
//			final byte[] result,
//			final String error,
//			final byte[] bloom,
//			final long gasUsed,
//			final List<EvmLog> logs,
//			final List<EntityId> createdContractIds,
//			final byte[] evmAddress,
//			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
//			final long gas,
//			final long amount,
//			final byte[] functionParameters,
//			final EntityId senderId
//	) {
//		this.contractId = contractId;
//		this.result = result;
//		this.error = error;
//		this.bloom = bloom;
//		this.gasUsed = gasUsed;
//		this.logs = logs;
//		this.createdContractIds = createdContractIds;
//		this.evmAddress = evmAddress;
//		this.stateChanges = stateChanges;
//		this.gas = gas;
//		this.amount = amount;
//		this.functionParameters = functionParameters;
//		this.senderId = senderId;
//	}

	/* --- SelfSerializable --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return RELEASE_0240_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		gasUsed = in.readLong();
		bloom = in.readByteArray(EvmLog.MAX_BLOOM_BYTES);
		result = in.readByteArray(MAX_RESULT_BYTES);
		error = readNullableString(in, MAX_ERROR_BYTES);
		contractId = readNullableSerializable(in);
		logs = in.readSerializableList(MAX_LOGS, true, EvmLog::new);
		createdContractIds = in.readSerializableList(MAX_CREATED_IDS, true, EntityId::new);
		// Added in 0.23
		evmAddress = in.readByteArray(MAX_ADDRESS_BYTES);
		// Added in 0.24
		if (version >= RELEASE_0240_VERSION && version < RELEASE_0280_VERSION) {
			int numAffectedContracts = in.readInt();
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> state = new TreeMap<>();
			while (numAffectedContracts-- > 0) {
				final byte[] contractAddress = in.readByteArray(MAX_ADDRESS_BYTES);
				int numAffectedSlots = in.readInt();
				final Map<Bytes, Pair<Bytes, Bytes>> storage = new TreeMap<>();
				state.put(Address.fromHexString(hex(contractAddress)), storage);
				while (numAffectedSlots-- > 0) {
					final var slot = Bytes.wrap(in.readByteArray(32));
					final var left = Bytes.wrap(in.readByteArray(32));
					final var hasRight = in.readBoolean();
					final var right = hasRight ? Bytes.wrap(in.readByteArray(32)) : null;
					storage.put(slot, Pair.of(left, right));
				}
			}
			stateChanges = state;
		}
		if (version >= RELEASE_0250_VERSION) {
			gas = in.readLong();
			amount = in.readLong();
			functionParameters = in.readByteArray(MAX_FUNCTION_PARAMETERS_BYTES);
		}
		if (version >= RELEASE_0260_VERSION) {
			senderId = readNullableSerializable(in);
		}
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(gasUsed);
		out.writeByteArray(bloom);
		out.writeByteArray(result);
		writeNullableString(error, out);
		writeNullableSerializable(contractId, out);
		out.writeSerializableList(logs, true, true);
		out.writeSerializableList(createdContractIds, true, true);
		out.writeByteArray(evmAddress);
		out.writeLong(gas);
		out.writeLong(amount);
		out.writeByteArray(functionParameters);
		writeNullableSerializable(senderId, out);
	}

	/* --- Object --- */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || EvmFnResult.class != o.getClass()) {
			return false;
		}
		var that = (EvmFnResult) o;
		return gasUsed == that.gasUsed &&
				Objects.equals(contractId, that.contractId) &&
				Arrays.equals(result, that.result) &&
				Objects.equals(error, that.error) &&
				Arrays.equals(bloom, that.bloom) &&
				Objects.equals(logs, that.logs) &&
				Objects.equals(createdContractIds, that.createdContractIds) &&
				Arrays.equals(evmAddress, that.evmAddress) &&
				this.stateChanges.equals(that.stateChanges) &&
				gas == that.gas &&
				amount == that.amount &&
				Arrays.equals(functionParameters, that.functionParameters) && 
			    Objects.equals(senderId, that.senderId);
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
				.add("gas", gas)
				.add("amount", amount)
				.add("functionParameters", hex(functionParameters))
				.add("senderId", senderId)
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

	public List<EvmLog> getLogs() {
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

	public long getGas() {
		return gas;
	}

	public long getAmount() {
		return amount;
	}

	public byte[] getFunctionParameters() {
		return functionParameters;
	}

	public EntityId getSenderId() {
		return senderId;
	}

	public void setEvmAddress(final byte[] evmAddress) {
		this.evmAddress = evmAddress;
	}

	public void setStateChanges(final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
		this.stateChanges = stateChanges;
	}

	public void setGas(final long gas) {
		this.gas = gas;
	}

	public void setAmount(final long amount) {
		this.amount = amount;
	}

	public void setFunctionParameters(final byte[] functionParameters) {
		this.functionParameters = functionParameters;
	}

	public void setSenderId(final EntityId senderId) {
		this.senderId = senderId;
	}

	public void updateForEvmCall(EthTxData callContext, EntityId senderId) {
		setGas(callContext.gasLimit());
		setAmount(callContext.getAmount());
		setFunctionParameters(callContext.callData());
		setSenderId(senderId);
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
			grpc.addAllLogInfo(logs.stream().map(EvmLog::toGrpc).toList());
		}
		if (isNotEmpty(createdContractIds)) {
			grpc.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).toList());
		}
		if (evmAddress.length > 0) {
			grpc.setEvmAddress(BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)));
		}
		//		stateChanges.forEach((address, slotAccesses) -> {
//			final var builder = ContractStateChange.newBuilder()
//					.setContractId(EntityIdUtils.contractIdFromEvmAddress(address.toArrayUnsafe()));
//			slotAccesses.forEach((slot, access) -> builder.addStorageChanges(trimmedGrpc(slot, access)));
//			grpc.addStateChanges(builder);
//		});
		grpc.setGas(gas);
		grpc.setAmount(amount);
		grpc.setFunctionParameters(ByteString.copyFrom(functionParameters));
		if (senderId != null) {
			grpc.setSenderId(senderId.toGrpcAccountId());
		}
		return grpc.build();
	}

	private static byte[] bloomFor(final List<Log> logs) {
		return LogsBloomFilter.builder().insertLogs(logs).build().toArray();
	}

	private static List<EntityId> serializableIdsFrom(final List<ContractID> grpcCreations) {
		final var n = grpcCreations.size();
		if (n > 0) {
			final List<EntityId> createdContractIds = new ArrayList<>();
			for (ContractID grpcCreation : grpcCreations) {
				createdContractIds.add(EntityId.fromGrpcContractId(grpcCreation));
			}
			return createdContractIds;
		} else {
			return Collections.emptyList();
		}
	}

	private static EvmFnResult success(
			final List<Log> logs,
			final long gasUsed,
			final Bytes output,
			final Address recipient,
			final List<EntityId> createdContractIds,
			final byte[] evmAddress
	) {
		return new EvmFnResult(
				EntityId.fromAddress(recipient),
				output.toArrayUnsafe(),
				null,
				bloomFor(logs),
				gasUsed,
				EvmLog.fromBesu(logs),
				createdContractIds,
				evmAddress,
//				Collections.emptyMap(),
				0L,
				0L,
				EMPTY,
				null);
	}


	private static EvmFnResult failure(
			final long gasUsed,
			final String error
	) {
		return new EvmFnResult(
				null,
				EMPTY,
				error,
				EMPTY,
				gasUsed,
				Collections.emptyList(),
				Collections.emptyList(),
				EMPTY,
//				Collections.emptyMap(),
				0L,
				0L,
				EMPTY,
				null);
	}
}
