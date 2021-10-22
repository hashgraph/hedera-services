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
import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.ContractAction;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class SolidityAction implements SelfSerializable {
	public static final int MAX_INPUT_BYTES = 32 * 1024;
	public static final int MAX_OUTPUT_BYTES = 32 * 1024;
	public static final int MAX_REVERT_BYTES = 32 * 1024;
	public static final int MAX_ERROR_BYTES = 32 * 1024;
	public static final int SOLIDITY_ADDRESS_LENGTH = 20;
	static final int MERKLE_VERSION = 1;
	// FIXME where does this number come from?
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x1533DEADC0DEL;
	private static final byte[] MISSING_BYTES = new byte[0];
	static DomainSerdes serdes = new DomainSerdes();
	private ContractActionType callType;
	private EntityId callingAccount;
	private EntityId callingContract;
	private long gas;
	private byte[] input = MISSING_BYTES;
	private EntityId recipientAccount;
	private EntityId recipientContract;
	// not set to empty bytes because it's a protobuf oneof
	private byte[] invalidSolidityAddress;
	private long value;
	private long gasUsed;
	private boolean success;
	// not set to empty bytes because it's a protobuf oneof
	private byte[] output;
	// not set to empty bytes because it's a protobuf oneof
	private byte[] revertReason;
	// not set to empty bytes because it's a protobuf oneof
	private byte[] error;
	private int callDepth;


	public SolidityAction() {
	}

	public SolidityAction(
			final ContractActionType callType,
			final EntityId callingAccount,
			final EntityId callingContract,
			final long gas,
			final byte[] input,
			final EntityId recipientAccount,
			final EntityId recipientContract,
			final byte[] invalidSolidityAddress,
			final long value,
			final long gasUsed,
			final boolean success,
			final byte[] output,
			final byte[] revertReason,
			final byte[] error,
			final int callDepth) {
		this.callType = callType;
		this.callingAccount = callingAccount;
		this.callingContract = callingContract;
		this.gas = gas;
		this.input = input == null ? MISSING_BYTES : input;
		this.recipientAccount = recipientAccount;
		this.recipientContract = recipientContract;
		this.invalidSolidityAddress = invalidSolidityAddress;
		this.value = value;
		this.gasUsed = gasUsed;
		this.success = success;
		this.output = output;
		this.revertReason = revertReason;
		this.error = error;
		this.callDepth = callDepth;
	}

	/* --- SelfSerializable --- */

	public static SolidityAction fromGrpc(ContractAction that) {
		return new SolidityAction(
				ContractActionType.values()[that.getCallType().getNumber()],
				that.hasCallingAccount() ? EntityId.fromGrpcAccountId(that.getCallingAccount()) : null,
				that.hasCallingContract() ? EntityId.fromGrpcContractId(that.getCallingContract()) : null,
				that.getGas(),
				that.getInput().isEmpty() ? MISSING_BYTES : that.getInput().toByteArray(),
				that.hasRecipientAccount() ? EntityId.fromGrpcAccountId(that.getRecipientAccount()) : null,
				that.hasRecipientContract() ? EntityId.fromGrpcContractId(that.getRecipientContract()) : null,
				that.getRecipientCase() == ContractAction.RecipientCase.INVALIDSOLIDITYADDRESS ?
						that.getInvalidSolidityAddress().toByteArray() : null,
				that.getValue(),
				that.getGasUsed(),
				that.getSuccess(),
				that.getResultDataCase() == ContractAction.ResultDataCase.OUTPUT ? that.getOutput().toByteArray() :
						null,
				that.getResultDataCase() == ContractAction.ResultDataCase.REVERTREASON ?
						that.getRevertReason().toByteArray() : null,
				that.getResultDataCase() == ContractAction.ResultDataCase.ERROR ? that.getError().toByteArray() : null,
				that.getCallDepth());
	}

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
		callType = ContractActionType.values()[in.readInt()];
		callingAccount = serdes.readNullableSerializable(in);
		callingContract = serdes.readNullableSerializable(in);
		gas = in.readLong();
		input = in.readByteArray(MAX_INPUT_BYTES);
		recipientAccount = serdes.readNullableSerializable(in);
		recipientContract = serdes.readNullableSerializable(in);
		invalidSolidityAddress = serdes.readNullable(in, i -> i.readByteArray(SOLIDITY_ADDRESS_LENGTH));
		value = in.readLong();
		gasUsed = in.readLong();
		success = in.readBoolean();
		output = serdes.readNullable(in, i -> i.readByteArray(MAX_OUTPUT_BYTES));
		revertReason = serdes.readNullable(in, i -> i.readByteArray(MAX_REVERT_BYTES));
		error = serdes.readNullable(in, i -> i.readByteArray(MAX_ERROR_BYTES));
		callDepth = in.readInt();
	}

	/* --- Object --- */

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(callType.ordinal());
		serdes.writeNullableSerializable(callingAccount, out);
		serdes.writeNullableSerializable(callingContract, out);
		out.writeLong(gas);
		out.writeByteArray(input);
		serdes.writeNullableSerializable(recipientAccount, out);
		serdes.writeNullableSerializable(recipientContract, out);
		serdes.writeNullable(invalidSolidityAddress, out, (d, o) -> o.writeByteArray(d));
		out.writeLong(value);
		out.writeLong(gasUsed);
		out.writeBoolean(success);
		serdes.writeNullable(output, out, (d, o) -> o.writeByteArray(d));
		serdes.writeNullable(revertReason, out, (d, o) -> o.writeByteArray(d));
		serdes.writeNullable(error, out, (d, o) -> o.writeByteArray(d));
		out.writeInt(callDepth);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final SolidityAction that = (SolidityAction) o;
		return gas == that.gas &&
			   value == that.value &&
			   gasUsed == that.gasUsed &&
			   success == that.success &&
			   callDepth == that.callDepth &&
			   callType == that.callType &&
			   Objects.equals(callingAccount, that.callingAccount) &&
			   Objects.equals(callingContract, that.callingContract) &&
			   Arrays.equals(input, that.input) &&
			   Objects.equals(recipientAccount, that.recipientAccount) &&
			   Objects.equals(recipientContract, that.recipientContract) &&
			   Arrays.equals(invalidSolidityAddress, that.invalidSolidityAddress) &&
			   Arrays.equals(output, that.output) &&
			   Arrays.equals(revertReason, that.revertReason) &&
			   Arrays.equals(error, that.error);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(
				callType,
				callingAccount,
				callingContract,
				gas,
				recipientAccount,
				recipientContract,
				value,
				gasUsed,
				success,
				callDepth);
		result = 31 * result + Arrays.hashCode(input);
		result = 31 * result + Arrays.hashCode(invalidSolidityAddress);
		result = 31 * result + Arrays.hashCode(output);
		result = 31 * result + Arrays.hashCode(revertReason);
		result = 31 * result + Arrays.hashCode(error);
		return result;
	}

	/* --- Bean --- */


	public ContractActionType getCallType() {
		return callType;
	}

	public long getGas() {
		return gas;
	}

	public byte[] getInput() {
		return input;
	}

	public byte[] getInvalidSolidityAddress() {
		return invalidSolidityAddress;
	}

	public long getValue() {
		return value;
	}

	public void setGasUsed(long gasUsed) {
		this.gasUsed = gasUsed;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(final boolean success) {
		this.success = success;
	}

	public byte[] getOutput() {
		return output;
	}

	public void setOutput(final byte[] output) {
		this.output = output;
	}

	public void setRevertReason(final byte[] revertReason) {
		this.revertReason = revertReason;
	}

	public byte[] getError() {
		return error;
	}

	public void setError(final byte[] error) {
		this.error = error;
	}

	/* --- Helpers --- */

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("callType", callType)
				.add("callingAccount", callingAccount)
				.add("callingContract", callingContract)
				.add("gas", gas)
				.add("input", CommonUtils.hex(input))
				.add("recipientAccount", recipientAccount)
				.add("recipientContract", recipientContract)
				.add("invalidSolidityAddress", CommonUtils.hex(invalidSolidityAddress))
				.add("value", value)
				.add("gasUsed", gasUsed)
				.add("success", success)
				.add("output", CommonUtils.hex(output))
				.add("revertReason", CommonUtils.hex(revertReason))
				.add("error", CommonUtils.hex(error))
				.add("callDepth", callDepth)
				.toString();
	}

	public ContractAction toGrpc() {
		var grpc = ContractAction.newBuilder();
		grpc.setCallType(com.hederahashgraph.api.proto.java.ContractActionType.forNumber(callType.ordinal()));
		if (callingAccount != null) {
			grpc.setCallingAccount(callingAccount.toGrpcAccountId());
		}
		if (callingContract != null) {
			grpc.setCallingContract(callingContract.toGrpcContractId());
		}
		grpc.setGas(gas);
		grpc.setInput(ByteString.copyFrom(input));
		if (recipientAccount != null) {
			grpc.setRecipientAccount(recipientAccount.toGrpcAccountId());
		}
		if (recipientContract != null) {
			grpc.setRecipientContract(recipientContract.toGrpcContractId());
		}
		if (invalidSolidityAddress != null) {
			grpc.setInvalidSolidityAddress(ByteString.copyFrom(invalidSolidityAddress));
		}
		grpc.setValue(value);
		grpc.setGasUsed(gasUsed);
		grpc.setSuccess(success);
		if (success) {
			grpc.setOutput(ByteString.copyFrom(output == null ? MISSING_BYTES : output));
		} else if (revertReason != null) {
			grpc.setRevertReason(ByteString.copyFrom(revertReason));
		} else {
			grpc.setError(ByteString.copyFrom(error == null ? MISSING_BYTES : error));
		}
		grpc.setCallDepth(callDepth);
		return grpc.build();
	}
}
