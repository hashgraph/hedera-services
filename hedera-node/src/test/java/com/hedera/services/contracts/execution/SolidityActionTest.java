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
package com.hedera.services.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.contracts.execution.traceability.CallOperationType;
import com.hedera.services.contracts.execution.traceability.ContractActionType;
import com.hedera.services.contracts.execution.traceability.SolidityAction;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.utils.EntityIdUtils;
import java.nio.charset.StandardCharsets;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class SolidityActionTest {

    private static final EntityId sender = EntityId.fromAddress(Address.BLS12_MAP_FP_TO_G1);
    private static final EntityId recipient = EntityId.fromAddress(Address.ALTBN128_ADD);
    private final byte[] input = "input".getBytes();
    private final byte[] output = "output".getBytes(StandardCharsets.UTF_8);
    private final long value = 55L;
    private final long gasUsed = 555L;
    private final long gas = 100L;
    ;

    @Test
    void toGrpcWhenCallingAccountAndRecipientAccountAndOutputAreSet() {
        final var actual = new SolidityAction(ContractActionType.CALL, gas, input, value, 0);
        actual.setCallingAccount(sender);
        actual.setRecipientAccount(recipient);
        actual.setGasUsed(gasUsed);
        actual.setOutput(output);
        actual.setCallOperationType(CallOperationType.OP_CALL);

        final var expected =
                ContractAction.newBuilder()
                        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                        .setCallingAccount(EntityIdUtils.asAccount(sender))
                        .setGas(gas)
                        .setInput(ByteStringUtils.wrapUnsafely(input))
                        .setRecipientAccount(EntityIdUtils.asAccount(recipient))
                        .setValue(value)
                        .setGasUsed(gasUsed)
                        .setOutput(ByteStringUtils.wrapUnsafely(output))
                        .setCallDepth(0)
                        .setCallOperationType(
                                com.hedera.services.stream.proto.CallOperationType.OP_CALL)
                        .build();

        assertEquals(expected, actual.toGrpc());
    }

    @Test
    void toGrpcWhenCallingContractAndRecipientContractAndRevertReasonAreSet() {
        final var actual = new SolidityAction(ContractActionType.CALL, gas, input, value, 0);
        actual.setCallingContract(sender);
        actual.setRecipientContract(recipient);
        actual.setGasUsed(gasUsed);
        actual.setRevertReason(output);
        actual.setCallOperationType(CallOperationType.OP_CALL);

        final var expected =
                ContractAction.newBuilder()
                        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                        .setCallingContract(
                                EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
                        .setGas(gas)
                        .setInput(ByteStringUtils.wrapUnsafely(input))
                        .setRecipientContract(
                                EntityIdUtils.contractIdFromEvmAddress(recipient.toEvmAddress()))
                        .setValue(value)
                        .setGasUsed(gasUsed)
                        .setRevertReason(ByteStringUtils.wrapUnsafely(output))
                        .setCallDepth(0)
                        .setCallOperationType(
                                com.hedera.services.stream.proto.CallOperationType.OP_CALL)
                        .build();

        assertEquals(expected, actual.toGrpc());
    }

    @Test
    void toGrpcWhenInvalidRecipientAndErrorAreSet() {
        final var actual = new SolidityAction(ContractActionType.CALL, gas, input, value, 0);
        actual.setGasUsed(gasUsed);
        actual.setError(output);
        actual.setInvalidSolidityAddress(recipient.toEvmAddress().toArrayUnsafe());
        actual.setCallingContract(sender);
        actual.setCallOperationType(CallOperationType.OP_CALL);

        final var expected =
                ContractAction.newBuilder()
                        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                        .setCallingContract(
                                EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
                        .setGas(gas)
                        .setInput(ByteStringUtils.wrapUnsafely(input))
                        .setInvalidSolidityAddress(
                                ByteStringUtils.wrapUnsafely(
                                        recipient.toEvmAddress().toArrayUnsafe()))
                        .setValue(value)
                        .setGasUsed(gasUsed)
                        .setError(ByteStringUtils.wrapUnsafely(output))
                        .setCallDepth(0)
                        .setCallOperationType(
                                com.hedera.services.stream.proto.CallOperationType.OP_CALL)
                        .build();

        assertEquals(expected, actual.toGrpc());
    }
}
