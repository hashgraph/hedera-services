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

package com.swirlds.pairings.bls12381.impl;

import com.swirlds.pairings.api.Field;
import com.swirlds.pairings.api.FieldElement;
import com.swirlds.pairings.bls12381.impl.jni.Bls12381Bindings;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

/**
 * Represents a field element in BLS12-381
 */
public class Bls12381FieldElement implements FieldElement {
    /** The field the element is in */
    private static final Bls12381Field FIELD = Bls12381Field.getInstance();

    /** The byte representation of the element */
    private final byte[] fieldElement;

    /**
     * Package private constructor
     *
     * @param fieldElement an array of bytes representing this field element
     */
    public Bls12381FieldElement(final byte[] fieldElement) {
        if (fieldElement == null) {
            throw new IllegalArgumentException("fieldElement parameter must not be null");
        }

        this.fieldElement = fieldElement;
    }

    @Override
    public boolean isSameField(@NonNull FieldElement otherElement) {
        return FieldElement.super.isSameField(otherElement);
    }

    @Override
    public int size() {
        return FieldElement.super.size();
    }

    @NonNull
    @Override
    public Field getField() {
        return FIELD;
    }

    @NonNull
    @Override
    public FieldElement add(@NonNull final FieldElement other) {
        if (!(other instanceof final Bls12381FieldElement otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381FieldElement");
        }

        final byte[] output = new byte[Bls12381Field.ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.scalarAdd(this, otherElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("scalarAdd", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public FieldElement subtract(@NonNull final FieldElement other) {
        if (!(other instanceof final Bls12381FieldElement otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381FieldElement");
        }

        final byte[] output = new byte[Bls12381Field.ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.scalarSubtract(this, otherElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("scalarSubtract", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public FieldElement multiply(@NonNull final FieldElement other) {
        if (!(other instanceof final Bls12381FieldElement otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381FieldElement");
        }

        final byte[] output = new byte[Bls12381Field.ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.scalarMultiply(this, otherElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("scalarMultiply", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public FieldElement power(@NonNull final BigInteger exponent) {
        if (exponent == null) {
            throw new IllegalArgumentException("exponent cannot be null");
        }

        final byte[] output = new byte[Bls12381Field.ELEMENT_BYTE_SIZE];

        final int errorCode = Bls12381Bindings.scalarPower(this, exponent.toByteArray(), output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("scalarPower", errorCode);
        }

        return new Bls12381FieldElement(output);
    }

    @NonNull
    @Override
    public BigInteger toBigInteger() {
        return null;
    }

    @Override
    @NonNull
    public byte[] toBytes() {
        return fieldElement;
    }

    // TODO: implement equals and hashCode
}
