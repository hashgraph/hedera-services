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

package com.hedera.cryptography.signaturescheme.api;

import com.hedera.cryptography.pairings.api.BilinearPairing;
import com.hedera.cryptography.pairings.api.Curve;
import com.hedera.cryptography.pairings.api.Field;
import com.hedera.cryptography.pairings.api.Group;
import com.hedera.cryptography.pairings.spi.BilinearPairingService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Represents a threshold signature schema.
 *
 * @implNote Given that we pack the type of the curve in serialized forms in 1 byte alongside other information
 * we can only support a limited amount of curves (128).
 * <p>
 */
public class SignatureSchema {
    private final BilinearPairing pairing;
    private final GroupAssignment groupAssignment;
    private final Curve curve;

    /**
     * Returns a signature scheme from its byte representation
     * <p>
     * The input byte is expected to be a packed byte, with the first 7 bits representing the curve type, and the last
     * bit representing the group assignment.
     *
     * @param idByte the byte representation of signature schema
     * @return the SignatureSchema instance
     */
    @NonNull
    public static SignatureSchema fromIdByte(final byte idByte) {
        final byte curveIdByte = BytePacker.unpackCurveType(idByte);
        final GroupAssignment groupAssignment = BytePacker.unpackGroupAssignment(idByte);
        final Curve curve = Curve.fromId(curveIdByte);
        return create(curve,groupAssignment);
    }

    /**
     * Returns a signature scheme a curve and a groupAssignment
     *
     * @param groupAssignment the group assignment
     * @param curve           the curve
     * @return the SignatureSchema instance
     */
    @NonNull
    public static SignatureSchema create(Curve curve, GroupAssignment groupAssignment) {
        return new SignatureSchema(BilinearPairingService.instanceOf(curve), groupAssignment ,curve);
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
     * Get the ID byte representing this schema
     *
     * @return the ID byte
     */
    public byte getIdByte() {
        return BytePacker.pack(groupAssignment, curve.getId());
    }

    /**
     * Get the bilinear pairing used for this schema
     *
     * @return the bilinear pairing
     */
    @NonNull
    public BilinearPairing getPairing() {
        return pairing;
    }

    /**
     * Constructor
     *
     * @param pairing         the pairing
     * @param groupAssignment the group assignment
     * @param curve           the curve
     */
    private SignatureSchema(
            @NonNull final BilinearPairing pairing, @NonNull final GroupAssignment groupAssignment, final Curve curve) {
        this.pairing = Objects.requireNonNull(pairing);
        this.groupAssignment = Objects.requireNonNull(groupAssignment);
        this.curve = curve;
    }

    /**
     * Packs and unpacks the curve type and group assignment into a single byte
     */
    private static class BytePacker {
        private static final int G_ASSIGNMENT_MASK = 0b10000000; // 1 bit for GroupAssignment
        private static final int CURVE_MASK = 0b01111111; // 7 bits for curve type

        /**
         * Packs the group assignment and curve type into a single byte
         *
         * @param groupAssignment the group assignment
         * @param curveType       the curve type
         * @return the packed byte
         */
        public static byte pack(@NonNull final GroupAssignment groupAssignment, final byte curveType) {
            if (curveType < 0) {
                throw new IllegalArgumentException("Curve type must be between 0 and 127");
            }

            final int assignmentValue = groupAssignment.ordinal() << 7;
            return (byte) (assignmentValue | (curveType & CURVE_MASK));
        }

        /**
         * Unpacks the group assignment from a packed byte
         *
         * @param packedByte the packed byte
         * @return the group assignment
         */
        public static GroupAssignment unpackGroupAssignment(final byte packedByte) {
            final int schemaValue = (packedByte & G_ASSIGNMENT_MASK) >> 7;
            return GroupAssignment.values()[schemaValue];
        }

        /**
         * Unpacks the curve type from a packed byte
         *
         * @param packedByte the packed byte
         * @return the curve type
         */
        public static byte unpackCurveType(final byte packedByte) {
            return (byte) (packedByte & CURVE_MASK);
        }
    }
}
