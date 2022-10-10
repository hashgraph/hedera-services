/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.sourcing;

import static com.hedera.services.legacy.proto.utils.ByteStringUtils.unwrapUnsafelyIfPossible;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.SignatureMap;

public class PojoSigMap {
    private static final int PUB_KEY_PREFIX_INDEX = 0;
    private static final int SIG_BYTES_INDEX = 1;
    private static final int DATA_PER_SIG_PAIR = 2;

    private final KeyType[] keyTypes;
    private final byte[][][] rawMap;

    private PojoSigMap(final byte[][][] rawMap, final KeyType[] keyTypes) {
        this.rawMap = rawMap;
        this.keyTypes = keyTypes;
    }

    public static PojoSigMap fromGrpc(final SignatureMap sigMap) {
        final var n = sigMap.getSigPairCount();
        final var rawMap = new byte[n][DATA_PER_SIG_PAIR][];
        final var keyTypes = new KeyType[n];
        for (var i = 0; i < n; i++) {
            final var sigPair = sigMap.getSigPair(i);
            rawMap[i][PUB_KEY_PREFIX_INDEX] = unwrapUnsafelyIfPossible(sigPair.getPubKeyPrefix());
            if (!sigPair.getECDSASecp256K1().isEmpty()) {
                rawMap[i][SIG_BYTES_INDEX] = unwrapUnsafelyIfPossible(sigPair.getECDSASecp256K1());
                keyTypes[i] = KeyType.ECDSA_SECP256K1;
            } else {
                rawMap[i][SIG_BYTES_INDEX] = unwrapUnsafelyIfPossible(sigPair.getEd25519());
                keyTypes[i] = KeyType.ED25519;
            }
        }
        return new PojoSigMap(rawMap, keyTypes);
    }

    public boolean isFullPrefixAt(final int i) {
        if (i < 0 || i >= rawMap.length) {
            throw new IllegalArgumentException(
                    "Requested prefix at index " + i + ", not in [0, " + rawMap.length + ")");
        }
        return keyTypes[i].getLength() == rawMap[i][PUB_KEY_PREFIX_INDEX].length;
    }

    public KeyType keyType(int i) {
        return keyTypes[i];
    }

    public byte[] pubKeyPrefix(int i) {
        return rawMap[i][PUB_KEY_PREFIX_INDEX];
    }

    public byte[] primitiveSignature(int i) {
        return rawMap[i][SIG_BYTES_INDEX];
    }

    public int numSigsPairs() {
        return rawMap.length;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("keyTypes", keyTypes)
                .add("rawMap", rawMap)
                .toString();
    }
}
