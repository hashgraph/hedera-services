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

package com.swirlds.platform.hcm.api;

import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.impl.internal.GroupAssignment;
import com.swirlds.platform.hcm.impl.pairings.bls12381.Bls12381BilinearPairing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Represents elliptic curves used in cryptographic protocols.
 *
 * @implNote Given that we pack the type of the curve in serialized forms in 1 byte alongside other information
 * we can only support a limited amount of curves.
 * <p>
 * TODO: define how many we use 2 bytes for type(publicKey,privateKey,signature) possibly need other 2 bytes for
 * (groupElement1 groupElement2 fieldElement) and the remaining for the type of curve
 */
public enum SignatureSchema {
    /**
     * BLS12-381: An elliptic curve providing 128-bit security, efficient for pairing-based cryptographic operations.
     * <p>
     * Signature group: G1
     */
    BLS12_381_G1_SIG(Bls12381BilinearPairing.getInstance(), GroupAssignment.GROUP1_FOR_SIGNING),
    /**
     * BLS12-381: An elliptic curve providing 128-bit security, efficient for pairing-based cryptographic operations.
     * <p>
     * Signature group: G2
     */
    BLS12_381_G2_SIG(Bls12381BilinearPairing.getInstance(), GroupAssignment.GROUP1_FOR_PUBLIC_KEY),
    /**
     * ALT_BN_128: Also known as BN256, this curve offers 128-bit security and efficient pairings, used in smart
     * contract platforms.
     */
    ALT_BN_128(null, null);

    private final BilinearPairing pairing;
    private final GroupAssignment groupAssignment;

    /**
     * Returns the byte representation of the curve type
     *
     * @return the byte representation of the curve type
     */
    public byte getIdByte() {
        return (byte) ordinal();
    }

    /**
     * Returns the curve type from its byte representation
     *
     * @param idByte the byte representation of the curve type
     * @return the curve type
     */
    @NonNull
    public static SignatureSchema fromIdByte(final byte idByte) {
        if (idByte < 0 || idByte > 63) {
            throw new IllegalArgumentException("Invalid idByte for curveType: " + idByte);
        }
        return SignatureSchema.values()[idByte];
    }

    /**
     * Get the group used for public keys
     *
     * @return the group used for public keys
     */
    @NonNull
    public Group getPublicKeyGroup() {
        return groupAssignment.getPublicKeyGroupFor(pairing);
    }

    /**
     * Get the group used for signatures
     *
     * @return the group used for signatures
     */
    @NonNull
    public Group getSignatureGroup() {
        return groupAssignment.getSignatureGroupFor(pairing);
    }

    /**
     * Get the field used for the curve
     *
     * @return the field used for the curve
     */
    @NonNull
    public Field getField() {
        return pairing.getField();
    }

    /**
     * Constructor
     *
     * @param pairing         the pairing
     * @param groupAssignment the group assignment
     */
    SignatureSchema(@NonNull final BilinearPairing pairing, @NonNull final GroupAssignment groupAssignment) {
        this.pairing = Objects.requireNonNull(pairing);
        this.groupAssignment = Objects.requireNonNull(groupAssignment);
    }
}
