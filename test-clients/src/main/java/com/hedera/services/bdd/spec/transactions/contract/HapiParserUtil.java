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
package com.hedera.services.bdd.spec.transactions.contract;

import static java.lang.System.arraycopy;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class HapiParserUtil {

    private HapiParserUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static byte[] encodeParametersWithTuple(final Object[] params, final String abi) {
        byte[] callData = new byte[] {};

        if (!abi.isEmpty() && !abi.contains("<empty>")) {
            final var abiFunction = Function.fromJson(abi);
            callData = abiFunction.encodeCallWithArgs(params).array();
        }

        return callData;
    }

    public static byte[] encodeParametersForConstructor(final Object[] params, final String abi) {
        byte[] callData = new byte[] {};

        if (!abi.isEmpty() && !abi.contains("<empty>")) {
            final var abiFunction = Function.fromJson(abi);
            callData = abiFunction.encodeCallWithArgs(params).array();
        }

        return stripSelector(callData);
    }

    public static Address convertAliasToAddress(final AccountID account) {
        final var besuAddress = asTypedEvmAddress(account);
        return convertBesuAddressToHeadlongAddress(besuAddress);
    }

    public static Address convertAliasToAddress(final String address) {
        final var addressBytes =
                Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address convertAliasToAddress(final byte[] address) {
        final var addressBytes = Bytes.wrap(address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static org.hyperledger.besu.datatypes.Address asTypedEvmAddress(final AccountID id) {
        return org.hyperledger.besu.datatypes.Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static byte[] asEvmAddress(final AccountID id) {
        return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static byte[] asEvmAddress(final int shard, final long realm, final long num) {
        final byte[] evmAddress = new byte[20];

        arraycopy(Ints.toByteArray(shard), 0, evmAddress, 0, 4);
        arraycopy(Longs.toByteArray(realm), 0, evmAddress, 4, 8);
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);

        return evmAddress;
    }

    public static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
        byte[] expandedArray = new byte[32];

        System.arraycopy(
                bytesToExpand,
                0,
                expandedArray,
                expandedArray.length - bytesToExpand.length,
                bytesToExpand.length);
        return expandedArray;
    }

    private static byte[] stripSelector(final byte[] bytesToExpand) {
        byte[] expandedArray = new byte[bytesToExpand.length-4];

        System.arraycopy(
            bytesToExpand,
            4,
            expandedArray,
            0,
            bytesToExpand.length-4);
        return expandedArray;
    }

    static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            @NotNull final org.hyperledger.besu.datatypes.Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        address.toUnsignedBigInteger()));
    }
}
