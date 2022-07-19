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

import com.google.protobuf.BytesValue;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class SidecarUtils {

    private SidecarUtils() {}

    public static TransactionSidecarRecord.Builder createContractBytecodeSidecarFrom(
            final ContractID contractID, final byte[] initCode, final byte[] runtimeCode) {
        return TransactionSidecarRecord.newBuilder()
                .setBytecode(
                        ContractBytecode.newBuilder()
                                .setContractId(contractID)
                                .setInitcode(ByteStringUtils.wrapUnsafely(initCode))
                                .setRuntimeBytecode(ByteStringUtils.wrapUnsafely(runtimeCode))
                                .build());
    }

    public static TransactionSidecarRecord.Builder createContractBytecodeSidecarFrom(
            ContractID contractID, byte[] runtimeCode) {
        return TransactionSidecarRecord.newBuilder()
                .setBytecode(
                        ContractBytecode.newBuilder()
                                .setContractId(contractID)
                                .setRuntimeBytecode(ByteStringUtils.wrapUnsafely(runtimeCode))
                                .build());
    }

    public static TransactionSidecarRecord.Builder createStateChangesSidecarFrom(
            final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
        final var grpc = ContractStateChanges.newBuilder();
        stateChanges.forEach(
                (address, slotAccessPairs) -> {
                    final var builder =
                            ContractStateChange.newBuilder()
                                    .setContractId(
                                            EntityIdUtils.contractIdFromEvmAddress(
                                                    address.toArrayUnsafe()));
                    slotAccessPairs.forEach(
                            (slot, access) -> builder.addStorageChanges(trimmedGrpc(slot, access)));
                    grpc.addContractStateChanges(builder);
                });
        return TransactionSidecarRecord.newBuilder().setStateChanges(grpc.build());
    }

    static StorageChange.Builder trimmedGrpc(final Bytes slot, final Pair<Bytes, Bytes> access) {
        final var grpc =
                StorageChange.newBuilder()
                        .setSlot(
                                ByteStringUtils.wrapUnsafely(
                                        (slot.trimLeadingZeros().toArrayUnsafe())))
                        .setValueRead(
                                ByteStringUtils.wrapUnsafely(
                                        access.getLeft().trimLeadingZeros().toArrayUnsafe()));
        if (access.getRight() != null) {
            grpc.setValueWritten(
                    BytesValue.newBuilder()
                            .setValue(
                                    ByteStringUtils.wrapUnsafely(
                                            access.getRight().trimLeadingZeros().toArrayUnsafe())));
        }
        return grpc;
    }
}
