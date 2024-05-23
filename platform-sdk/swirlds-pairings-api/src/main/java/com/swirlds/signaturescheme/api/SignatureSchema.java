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

package com.swirlds.signaturescheme.api;

import com.swirlds.pairings.api.BilinearPairing;
import com.swirlds.pairings.api.Curve;
import com.swirlds.pairings.api.Field;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.spi.BilinearPairingService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Represents elliptic curves used in cryptographic protocols.
 *
 * @implNote Given that we pack the type of the curve in serialized forms in 1 byte alongside other information
 * we can only support a limited amount of curves.
 * <p>
 */
public class SignatureSchema {
    private final BilinearPairing pairing;
    private final GroupAssignment groupAssignment;
    private final Curve curve;

    /**
     * Returns the curve type from its byte representation
     *
     * @param byteArray the byte array with representation of the curve type
     * @return the curve type
     */
    @NonNull
    public static SignatureSchema fromIdByte(final byte[] byteArray) {
        Objects.requireNonNull(byteArray, "byteArray must not be null");
        return fromIdByte(byteArray[0]);
    }

    /**
     * Returns the curve type from its byte representation
     *
     * @return the curve type
     */
    @NonNull
    public static SignatureSchema forCurveAndType(final byte curveType, final @NonNull GroupAssignment assignment) {
        return forCurveAndType(Curve.formId(curveType), assignment);
    }

    /**
     * Returns the curve type from its byte representation
     *
     * @return the curve type
     */
    @NonNull
    public static SignatureSchema forCurveAndType(final Curve curve, final @NonNull GroupAssignment assignment) {
        return new SignatureSchema(BilinearPairingService.instanceOf(curve), assignment, curve);
    }

    /**
     * Returns the curve type from its byte representation
     *
     * @param idByte the byte representation of the curve type
     * @return the SignatureSchema instance to use
     */
    @NonNull
    public static SignatureSchema fromIdByte(final byte idByte) {
        return forCurveAndType(BytePacker.unpackCurveType(idByte), BytePacker.unpackGroupAssignment(idByte));
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

    public byte getIdByte() {
        return BytePacker.pack(groupAssignment, curve.getId());
    }

    public BilinearPairing getPairing() {
        return pairing;
    }

    /**
     * Constructor
     *
     * @param pairing         the pairing
     * @param groupAssignment the group assignment
     * @param groupAssignment the curve id
     */
    private SignatureSchema(
            @NonNull final BilinearPairing pairing, @NonNull final GroupAssignment groupAssignment, final Curve curve) {
        this.pairing = Objects.requireNonNull(pairing);
        this.groupAssignment = Objects.requireNonNull(groupAssignment);
        this.curve = curve;
    }

    private static class BytePacker {
        private static final int GASSIGNAMENT_MASK = 0b10000000; // 1 bit for GroupAssignment
        private static final int CURVE_MASK = 0b01111111; // 7 bits for curve type

        public static byte pack(GroupAssignment groupAssignament, byte curveType) {
            if (curveType < 0 || curveType > 63) {
                throw new IllegalArgumentException("Curve type must be between 0 and 63");
            }

            int assignamentValue = groupAssignament.ordinal() << 7;
            return (byte) (assignamentValue | (curveType & CURVE_MASK));
        }

        public static GroupAssignment unpackGroupAssignment(byte packedByte) {
            int schemaValue = (packedByte & GASSIGNAMENT_MASK) >> 7;
            return GroupAssignment.values()[schemaValue];
        }

        public static byte unpackCurveType(byte packedByte) {
            return (byte) (packedByte & CURVE_MASK);
        }
    }
}
