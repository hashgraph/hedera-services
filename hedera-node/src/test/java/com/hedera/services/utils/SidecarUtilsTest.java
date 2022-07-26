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
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.test.utils.IdUtils;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class SidecarUtilsTest {

    @Test
    void contractBytecodesAreCreatedAsExpected() {
        // given
        final var contract = IdUtils.asContract("0.0.6");
        final var initCode = "initCode".getBytes();
        final var runtimeCode = "runtimeCode".getBytes();

        // when
        final var bytecodeSidecar =
                SidecarUtils.createContractBytecodeSidecarFrom(contract, initCode, runtimeCode)
                        .build();

        // then
        final var expectedBytecodes =
                ContractBytecode.newBuilder()
                        .setContractId(contract)
                        .setInitcode(ByteString.copyFrom(initCode))
                        .setRuntimeBytecode(ByteString.copyFrom(runtimeCode))
                        .build();
        final var expectedTransactionSidecarRecord =
                TransactionSidecarRecord.newBuilder().setBytecode(expectedBytecodes).build();
        assertEquals(expectedTransactionSidecarRecord, bytecodeSidecar);
    }

    @Test
    void contractBytecodesWithoutInitCodeAreCreatedAsExpected() {
        // given
        final var contract = IdUtils.asContract("0.0.6");
        final var runtimeCode = "runtimeCode".getBytes();

        // when
        final var bytecodeSidecar =
                SidecarUtils.createContractBytecodeSidecarFrom(contract, runtimeCode).build();

        // then
        final var expectedBytecodes =
                ContractBytecode.newBuilder()
                        .setContractId(contract)
                        .setRuntimeBytecode(ByteString.copyFrom(runtimeCode))
                        .build();
        final var expectedTransactionSidecarRecord =
                TransactionSidecarRecord.newBuilder().setBytecode(expectedBytecodes).build();
        assertEquals(expectedTransactionSidecarRecord, bytecodeSidecar);
    }

    @Test
    void contractBytecodesForFailedCreatesAreCreatedAsExpected() {
        // given
        final var initCode = "initCode".getBytes();

        // when
        final var bytecodeSidecar =
                SidecarUtils.createContractBytecodeSidecarForFailedCreate(initCode).build();

        // then
        final var expectedBytecodes =
                ContractBytecode.newBuilder().setInitcode(ByteString.copyFrom(initCode)).build();
        final var expectedTransactionSidecarRecord =
                TransactionSidecarRecord.newBuilder().setBytecode(expectedBytecodes).build();
        assertEquals(expectedTransactionSidecarRecord, bytecodeSidecar);
    }

    @Test
    void stateChangesAreCreatedAsExpected() {
        // given
        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = new TreeMap<>();
        final var address = Address.fromHexString("0x4321");
        final var slot = Bytes.of(1);
        final var valueRead = Bytes.of(2);
        stateChanges.put(address, Map.of(slot, Pair.of(valueRead, null)));

        // when
        final var stateChangesSidecar =
                SidecarUtils.createStateChangesSidecarFrom(stateChanges).build();

        // then
        final var expectedStorageChange =
                StorageChange.newBuilder()
                        .setSlot(ByteString.copyFrom(slot.toArrayUnsafe()))
                        .setValueRead(ByteString.copyFrom(valueRead.toArrayUnsafe()))
                        .build();
        final var expectedTransactionSidecarRecord =
                TransactionSidecarRecord.newBuilder()
                        .setStateChanges(
                                ContractStateChanges.newBuilder()
                                        .addContractStateChanges(
                                                ContractStateChange.newBuilder()
                                                        .setContractId(
                                                                EntityIdUtils
                                                                        .contractIdFromEvmAddress(
                                                                                address))
                                                        .addStorageChanges(expectedStorageChange)
                                                        .build())
                                        .build())
                        .build();
        assertEquals(expectedTransactionSidecarRecord, stateChangesSidecar);
    }

    @Test
    void stripsLeadingZerosInChangeRepresentation() {
        final var slot = Bytes.wrap(Address.BLS12_G1MULTIEXP.toArray());
        final var access =
                Pair.of(
                        Bytes.of(Address.BLS12_MAP_FP2_TO_G2.toArray()),
                        Bytes.of(Address.BLS12_G1MUL.toArray()));
        final var expected =
                StorageChange.newBuilder()
                        .setSlot(
                                ByteString.copyFrom(
                                        Address.BLS12_G1MULTIEXP.trimLeadingZeros().toArray()))
                        .setValueRead(
                                ByteString.copyFrom(
                                        Address.BLS12_MAP_FP2_TO_G2.trimLeadingZeros().toArray()))
                        .setValueWritten(
                                BytesValue.newBuilder()
                                        .setValue(
                                                ByteString.copyFrom(
                                                        Address.BLS12_G1MUL
                                                                .trimLeadingZeros()
                                                                .toArray()))
                                        .build())
                        .build();
        final var actual = SidecarUtils.trimmedGrpc(slot, access);
        assertEquals(expected, actual.build());
    }
}
