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

package com.hedera.services.bdd.spec.transactions.contract;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.tuweni.bytes.Bytes;

public class HapiParserUtil {

    private HapiParserUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static byte[] encodeParametersForCall(final Object[] params, final String abi) {
        return encodeParameters(params, abi);
    }

    public static byte[] encodeParametersForConstructor(final Object[] params, final String abi) {
        return stripSelector(encodeParameters(params, abi));
    }

    private static byte[] encodeParameters(final Object[] params, final String abi) {
        byte[] callData = new byte[] {};
        if (!abi.isEmpty() && !abi.contains("<empty>")) {
            final var abiFunction = Function.fromJson(abi);
            callData = abiFunction.encodeCallWithArgs(params).array();
        }

        return callData;
    }

    public static Address asHeadlongAddress(final String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asHeadlongAddress(final byte[] address) {
        final var addressBytes = Bytes.wrap(address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address[] asHeadlongAddressArray(final byte[]... addresses) {
        Address[] headlongAddresses = new Address[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            headlongAddresses[i] = asHeadlongAddress(addresses[i]);
        }
        return headlongAddresses;
    }

    public static Address evmAddressFromSecp256k1Key(final Key key) {
        if (!key.getECDSASecp256K1().isEmpty()) {
            return asHeadlongAddress(EthSigsUtils.recoverAddressFromPubKey(
                    key.getECDSASecp256K1().toByteArray()));
        } else {
            throw new IllegalStateException("Cannot observe address for non-ECDSA key!");
        }
    }

    public static byte[] stripSelector(final byte[] bytesToExpand) {
        byte[] expandedArray = new byte[bytesToExpand.length - 4];

        System.arraycopy(bytesToExpand, 4, expandedArray, 0, bytesToExpand.length - 4);
        return expandedArray;
    }
}
