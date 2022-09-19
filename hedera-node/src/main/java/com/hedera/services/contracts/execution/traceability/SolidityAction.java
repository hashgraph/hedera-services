/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.contracts.execution.traceability;

import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.stream.proto.ContractAction;

public class SolidityAction {
    private static final byte[] MISSING_BYTES = new byte[0];

    private ContractActionType callType;
    private EntityId callingAccount;
    private EntityId callingContract;
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
    private CallOperationType callOperationType;

    public SolidityAction(
            final ContractActionType callType,
            final long gas,
            final byte[] input,
            final long value,
            final int callDepth) {
        this.callType = callType;
        this.gas = gas;
        this.input = input == null ? MISSING_BYTES : input;
        this.value = value;
        this.callDepth = callDepth;
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

    public void setCallOperationType(CallOperationType callOperationType) {
        this.callOperationType = callOperationType;
    }

    public void setCallingAccount(EntityId callingAccount) {
        this.callingAccount = callingAccount;
    }

    public void setCallingContract(EntityId callingContract) {
        this.callingContract = callingContract;
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

    public CallOperationType getCallOperationType() {
        return callOperationType;
    }

    public ContractAction toGrpc() {
        final var grpc = ContractAction.newBuilder();
        grpc.setCallType(
                com.hedera.services.stream.proto.ContractActionType.forNumber(callType.ordinal()));
        if (callingAccount != null) {
            grpc.setCallingAccount(callingAccount.toGrpcAccountId());
        } else if (callingContract != null) {
            grpc.setCallingContract(callingContract.toGrpcContractId());
        }
        grpc.setGas(gas);
        grpc.setInput(ByteStringUtils.wrapUnsafely(input));
        if (recipientAccount != null) {
            grpc.setRecipientAccount(recipientAccount.toGrpcAccountId());
        } else if (recipientContract != null) {
            grpc.setRecipientContract(recipientContract.toGrpcContractId());
        } else if (invalidSolidityAddress != null) {
            grpc.setInvalidSolidityAddress(ByteStringUtils.wrapUnsafely(invalidSolidityAddress));
        }
        grpc.setValue(value);
        grpc.setGasUsed(gasUsed);
        if (output != null) {
            grpc.setOutput(ByteStringUtils.wrapUnsafely(output));
        } else if (revertReason != null) {
            grpc.setRevertReason(ByteStringUtils.wrapUnsafely(revertReason));
        } else if (error != null) {
            grpc.setError(ByteStringUtils.wrapUnsafely(error));
        }
        grpc.setCallDepth(callDepth);
        grpc.setCallOperationType(
                com.hedera.services.stream.proto.CallOperationType.forNumber(
                        callOperationType.ordinal()));
        return grpc.build();
    }
}
