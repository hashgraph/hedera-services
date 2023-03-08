/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_CREATE;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.SolidityAction;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.services.stream.proto.ContractAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.api.SoftAssertions;
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

    @Test
    void isValidTest() {
        // "gasUsed" and missing "oneof recipient" omitted in these tests because those are okay never having been set

        final byte[] BYTES = new byte[5];
        record SACtor(boolean good, Supplier<SolidityAction> ctor) {}
        final var ctors = Map.of(
                "good",
                new SACtor(true, () -> new SolidityAction(CREATE, 5, BYTES, 7, 10)),
                "null call type",
                new SACtor(false, () -> new SolidityAction(null, 5, BYTES, 7, 10)),
                "bad call type",
                new SACtor(false, () -> new SolidityAction(ContractActionType.NO_ACTION, 5, BYTES, 7, 10)),
                "bad input", /* true because this one case is _fixed_ by SolidityAction's constructor */
                new SACtor(true, () -> new SolidityAction(CREATE, 5, null, 7, 10)),
                "good but empty input",
                new SACtor(true, () -> new SolidityAction(CREATE, 5, new byte[0], 7, 10)));
        final var fieldSetters = Map.<String, Consumer<SolidityAction>>of(
                "callingAccount", sa -> sa.setCallingAccount(sender),
                "callingContract", sa -> sa.setCallingContract(sender),
                "recipientAccount", sa -> sa.setRecipientAccount(sender),
                "recipientContract", sa -> sa.setRecipientContract(sender),
                "invalidSolidityAddress", sa -> sa.setTargetedAddress(BYTES),
                "output", sa -> sa.setOutput(BYTES),
                "revertReason", sa -> sa.setRevertReason(BYTES),
                "error", sa -> sa.setError(BYTES),
                "callOperationType", sa -> sa.setCallOperationType(CallOperationType.OP_DELEGATECALL));
        record SAFields(boolean good, String... fields) {}
        final var fields = List.<SAFields>of(
                new SAFields(true, "callingAccount", "recipientAccount", "output", "callOperationType"),
                new SAFields(true, "callingContract", "recipientContract", "revertReason", "callOperationType"),
                new SAFields(true, "callingAccount", "invalidSolidityAddress", "error", "callOperationType"),
                new SAFields(true, "callingContract", "recipientAccount", "revertReason", "callOperationType"),
                new SAFields(true, "callingAccount", "recipientContract", "output", "callOperationType"),
                new SAFields(false, "recipientAccount", "output", "callOperationType"),
                new SAFields(true, "callingAccount", "error", "callOperationType"),
                new SAFields(
                        false, "callingAccount", "callingContract", "recipientAccount", "output", "callOperationType"),
                new SAFields(true, "callingAccount", "output", "callOperationType"),
                new SAFields(
                        false,
                        "callingAccount",
                        "recipientAccount",
                        "invalidSolidityAddress",
                        "output",
                        "callOperationType"),
                new SAFields(
                        false,
                        "callingContract",
                        "recipientAccount",
                        "recipientContract",
                        "invalidSolidityAddress",
                        "output",
                        "callOperationType"),
                new SAFields(false, "callingContract", "invalidSolidityAddress", "callOperationType"),
                new SAFields(
                        false, "callingAccount", "recipientAccount", "output", "revertReason", "callOperationType"),
                new SAFields(
                        false, "callingContract", "recipientContract", "revertReason", "error", "callOperationType"),
                new SAFields(
                        false,
                        "callingAccount",
                        "invalidSolidityAddress",
                        "output",
                        "revertReason",
                        "error",
                        "callOperationType"),
                new SAFields(false, "callingContract", "recipientAccount", "output"));

        // sanity check validity of 'fields' test cases
        assertThat(fields.stream()
                        .map(SAFields::fields)
                        .flatMap(Arrays::stream)
                        .distinct()
                        .filter(k -> !fieldSetters.containsKey(k))
                        .sorted()
                        .toList())
                .as("sanity check that all field names specified in tests are correct has _failed_")
                .isEmpty();

        // now: test cases are cartesian product of constructor setup plus field setups
        int ncases = 0;
        int ngood = 0;
        final var softly = new SoftAssertions();
        for (final var ctorNV : ctors.entrySet()) {
            for (final var field : fields) {
                boolean expected = ctorNV.getValue().good() && field.good();

                // Construct the SolidityAction with the given constructor values
                final var sut = ctorNV.getValue().ctor().get();
                // Set fields to valid values with the given field names
                for (final var setter : field.fields()) {
                    fieldSetters.get(setter).accept(sut);
                }
                softly.assertThat(sut.isValid())
                        .as("ctor(%s)[%s]".formatted(ctorNV.getKey(), String.join(",", field.fields())))
                        .isEqualTo(expected);
                ncases++;
                if (expected) ngood++;
            }
        }
        softly.assertAll();
        assertThat(ncases).isEqualTo(80);
        assertThat(ngood).isEqualTo(21);
    }

    @Test
    void toGrpcWhenCallingAccountAndRecipientAccountAndOutputAreSet() {
        final var actual = new SolidityAction(ContractActionType.CALL, gas, input, value, 0);
        actual.setCallingAccount(sender);
        actual.setRecipientAccount(recipient);
        actual.setGasUsed(gasUsed);
        actual.setOutput(output);
        actual.setCallOperationType(CallOperationType.OP_CALL);

        final var expected = ContractAction.newBuilder()
                .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                .setCallingAccount(EntityIdUtils.asAccount(sender))
                .setGas(gas)
                .setInput(ByteStringUtils.wrapUnsafely(input))
                .setRecipientAccount(EntityIdUtils.asAccount(recipient))
                .setValue(value)
                .setGasUsed(gasUsed)
                .setOutput(ByteStringUtils.wrapUnsafely(output))
                .setCallDepth(0)
                .setCallOperationType(com.hedera.services.stream.proto.CallOperationType.OP_CALL)
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

        final var expected = ContractAction.newBuilder()
                .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                .setCallingContract(EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
                .setGas(gas)
                .setInput(ByteStringUtils.wrapUnsafely(input))
                .setRecipientContract(EntityIdUtils.contractIdFromEvmAddress(recipient.toEvmAddress()))
                .setValue(value)
                .setGasUsed(gasUsed)
                .setRevertReason(ByteStringUtils.wrapUnsafely(output))
                .setCallDepth(0)
                .setCallOperationType(com.hedera.services.stream.proto.CallOperationType.OP_CALL)
                .build();

        assertEquals(expected, actual.toGrpc());
    }

    @Test
    void toGrpcWhenInvalidRecipientAndErrorAreSet() {
        final var actual = new SolidityAction(ContractActionType.CALL, gas, input, value, 0);
        actual.setGasUsed(gasUsed);
        actual.setError(output);
        actual.setTargetedAddress(recipient.toEvmAddress().toArrayUnsafe());
        actual.setCallingContract(sender);
        actual.setCallOperationType(CallOperationType.OP_CALL);

        final var expected = ContractAction.newBuilder()
                .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
                .setCallingContract(EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
                .setGas(gas)
                .setInput(ByteStringUtils.wrapUnsafely(input))
                .setTargetedAddress(
                        ByteStringUtils.wrapUnsafely(recipient.toEvmAddress().toArrayUnsafe()))
                .setValue(value)
                .setGasUsed(gasUsed)
                .setError(ByteStringUtils.wrapUnsafely(output))
                .setCallDepth(0)
                .setCallOperationType(com.hedera.services.stream.proto.CallOperationType.OP_CALL)
                .build();

        assertEquals(expected, actual.toGrpc());
    }

    @Test
    void toFullStringTest() {
        var sut = new SolidityAction(CREATE, 7, null, 11, 13);
        assertThat(sut.toFullString())
                .isEqualTo(
                        """
                SolidityAction(callType: CREATE, callOperationType: <null>, value: 11, gas: 7, \
                gasUsed: 0, callDepth: 13, callingAccount: <null>, callingContract: <null>, \
                recipientAccount: <null>, recipientContract: <null>, invalidSolidityAddress \
                (aka targetedAddress): <null>, input: <empty>, output: <null>, \
                revertReason: <null>, error: <null>)\
                """);

        sut = new SolidityAction(CREATE, 7, new byte[] {0x20, 0x21, 0x22}, 11, 13);
        sut.setCallOperationType(OP_CREATE);
        sut.setGasUsed(17);
        sut.setCallingAccount(EntityId.fromNum(1234));
        sut.setCallingContract(EntityId.fromNum(2345));
        sut.setRecipientAccount(EntityId.fromNum(3456));
        sut.setRecipientContract(EntityId.fromNum(4567));
        sut.setTargetedAddress(new byte[] {0x10, 0x11, 0x12});
        sut.setOutput(new byte[] {0x30, 0x31, 0x32});
        sut.setRevertReason(new byte[] {0x40, 0x41, 0x42});
        sut.setError(new byte[] {0x50, 0x51, 0x52});
        assertThat(sut.toFullString())
                .isEqualTo(
                        """
                SolidityAction(callType: CREATE, callOperationType: OP_CREATE, value: 11, gas: 7, \
                gasUsed: 17, callDepth: 13, callingAccount: EntityId{shard=0, realm=0, num=1234}, \
                callingContract: EntityId{shard=0, realm=0, num=2345}, \
                recipientAccount: EntityId{shard=0, realm=0, num=3456}, \
                recipientContract: EntityId{shard=0, realm=0, num=4567}, \
                invalidSolidityAddress (aka targetedAddress): 0x101112, \
                input: 0x202122, output: 0x303132, revertReason: 0x404142, \
                error: 0x505152)\
                """);
    }
}
