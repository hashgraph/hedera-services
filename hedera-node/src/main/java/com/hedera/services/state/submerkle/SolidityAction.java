package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.stream.proto.ContractAction;
import com.swirlds.common.utility.CommonUtils;

import java.util.Arrays;
import java.util.Objects;

public class SolidityAction {
    private static final byte[] MISSING_BYTES = new byte[0];

    private ContractActionType callType;
    private final EntityId callingAccount;
    private final EntityId callingContract;
    private final long gas;
    private final byte[] input;
    private EntityId recipientAccount;
    private EntityId recipientContract;
    private byte[] invalidSolidityAddress;
    private final long value;
    private long gasUsed;
    private byte[] output;
    private byte[] revertReason;
    private byte[] error;
    private final int callDepth;

    public SolidityAction(
            final ContractActionType callType,
            final EntityId callingAccount,
            final EntityId callingContract,
            final long gas,
            final byte[] input,
            final EntityId recipientAccount,
            final EntityId recipientContract,
            final long value,
            final int callDepth) {
        this.callType = callType;
        this.callingAccount = callingAccount;
        this.callingContract = callingContract;
        this.gas = gas;
        this.input = input == null ? MISSING_BYTES : input;
        this.recipientAccount = recipientAccount;
        this.recipientContract = recipientContract;
        this.value = value;
        this.callDepth = callDepth;
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
                callDepth);
        result = 31 * result + Arrays.hashCode(input);
        result = 31 * result + Arrays.hashCode(invalidSolidityAddress);
        result = 31 * result + Arrays.hashCode(output);
        result = 31 * result + Arrays.hashCode(revertReason);
        result = 31 * result + Arrays.hashCode(error);
        return result;
    }

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
                .add("output", CommonUtils.hex(output))
                .add("revertReason", CommonUtils.hex(revertReason))
                .add("error", CommonUtils.hex(error))
                .add("callDepth", callDepth)
                .toString();
    }

    public ContractActionType getCallType() {
        return callType;
    }

    public long getGas() {
        return gas;
    }

    public byte[] getInput() {
        return input;
    }

    public long getValue() {
        return value;
    }

    public void setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
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

    public void setInvalidSolidityAddress(byte[] invalidSolidityAddress) {
        this.invalidSolidityAddress = invalidSolidityAddress;
    }

    public void setRecipientAccount(EntityId recipientAccount) {
        this.recipientAccount = recipientAccount;
    }

    public void setCallType(ContractActionType callType) {
        this.callType = callType;
    }

    public void setRecipientContract(EntityId recipientContract) {
        this.recipientContract = recipientContract;
    }

    public EntityId getRecipientAccount() {
        return recipientAccount;
    }

    public EntityId getRecipientContract() {
        return recipientContract;
    }

    public EntityId getCallingAccount() {
        return callingAccount;
    }

    public EntityId getCallingContract() {
        return callingContract;
    }

    public byte[] getInvalidSolidityAddress() {
        return invalidSolidityAddress;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public byte[] getRevertReason() {
        return revertReason;
    }

    public int getCallDepth() {
        return callDepth;
    }

    public ContractAction toGrpc() {
        var grpc = ContractAction.newBuilder();
        grpc.setCallType(com.hedera.services.stream.proto.ContractActionType.forNumber(callType.ordinal()));
        if (callingAccount != null) {
            grpc.setCallingAccount(callingAccount.toGrpcAccountId());
        } else if (callingContract != null) {
            grpc.setCallingContract(callingContract.toGrpcContractId());
        }
        grpc.setGas(gas);
        grpc.setInput(ByteString.copyFrom(input));
        if (recipientAccount != null) {
            grpc.setRecipientAccount(recipientAccount.toGrpcAccountId());
        } else if (recipientContract != null) {
            grpc.setRecipientContract(recipientContract.toGrpcContractId());
        } else if (invalidSolidityAddress != null) {
            grpc.setInvalidSolidityAddress(ByteString.copyFrom(invalidSolidityAddress));
        }
        grpc.setValue(value);
        grpc.setGasUsed(gasUsed);
        if (output != null) {
            grpc.setOutput(ByteString.copyFrom(output));
        } else if  (revertReason != null) {
            grpc.setRevertReason(ByteString.copyFrom(revertReason));
        } else if (error != null){
            grpc.setError(ByteString.copyFrom(error));
        }
        grpc.setCallDepth(callDepth);
        return grpc.build();
    }
}
