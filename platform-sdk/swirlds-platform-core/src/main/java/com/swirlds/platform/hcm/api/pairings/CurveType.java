/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.hcm.api.pairings;

/**
 * Represents elliptic curves used in cryptographic protocols.
 *
 * @implNote Given that we pack the type of the curve in serialized forms in 1 byte alongside other information
 * we can only support a limited amount of curves.
 *
 * TODO: define how many we use 2 bytes for type(publicKey,privateKey,signature) possibly need other 2 bytes for (groupElement1 groupElement2 fieldElement) and the remaining for the type of curve
 *
 */
public enum CurveType {
    /**
     * BLS12-381: An elliptic curve providing 128-bit security, efficient for pairing-based cryptographic operations.
     */
    BLS12_381,
    /**
     * ALT_BN_128: Also known as BN256, this curve offers 128-bit security and efficient pairings, used in smart contract platforms.
     */
    ALT_BN_128;

    public byte getId() {
        return (byte) ordinal();
    }

    public static CurveType fromIdByte(byte idByte) {
        if (idByte < 0 || idByte > 63) {
            throw new IllegalArgumentException("Invalid idByte for curveType: " + idByte);
        }
        return CurveType.values()[idByte];
    }
}
