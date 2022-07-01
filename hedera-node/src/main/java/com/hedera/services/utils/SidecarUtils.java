package com.hedera.services.utils;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Map;

public class SidecarUtils {

	public static TransactionSidecarRecord.Builder createContractBytecode(
			ContractID contractID,
			byte[] initCode,
			byte[] runtimeCode
	) {
		return TransactionSidecarRecord.newBuilder().setBytecode(ContractBytecode.newBuilder()
				.setContractId(contractID)
				.setInitcode(ByteStringUtils.wrapUnsafely(initCode))
				.setRuntimeBytecode(ByteStringUtils.wrapUnsafely(runtimeCode))
				.build());
	}

	public static TransactionSidecarRecord.Builder createStateChangesSidecar(
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges
	) {
		final var grpc = ContractStateChanges.newBuilder();
		stateChanges.forEach((address, slotAccesses) -> {
			final var builder = ContractStateChange.newBuilder()
					.setContractId(EntityIdUtils.contractIdFromEvmAddress(address.toArrayUnsafe()));
			slotAccesses.forEach((slot, access) -> builder.addStorageChanges(trimmedGrpc(slot, access)));
			grpc.addContractStateChanges(builder);
		});
		return TransactionSidecarRecord.newBuilder().setStateChanges(grpc.build());
	}

	static StorageChange.Builder trimmedGrpc(final Bytes slot, final Pair<Bytes, Bytes> access) {
		final var grpc = StorageChange.newBuilder()
				.setSlot(ByteString.copyFrom(slot.trimLeadingZeros().toArrayUnsafe()))
				.setValueRead(ByteString.copyFrom(access.getLeft().trimLeadingZeros().toArrayUnsafe()));
		if (access.getRight() != null) {
			grpc.setValueWritten(BytesValue.newBuilder().setValue(
					ByteString.copyFrom(access.getRight().trimLeadingZeros().toArrayUnsafe())));
		}
		return grpc;
	}
}
