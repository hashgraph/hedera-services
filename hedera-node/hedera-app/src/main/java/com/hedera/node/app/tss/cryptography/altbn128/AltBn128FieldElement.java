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

package com.hedera.node.app.tss.cryptography.altbn128;

import static com.hedera.node.app.tss.cryptography.utils.ValidationUtils.expectOrThrow;

import com.hedera.node.app.tss.cryptography.altbn128.adapter.jni.ArkBn254Adapter;
import com.hedera.node.app.tss.cryptography.altbn128.facade.FieldFacade;
import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.utils.ByteArrayUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * The implementation of a {@link FieldElement}
 * for {@link com.hedera.node.app.tss.cryptography.pairings.api.curves.KnownCurves#ALT_BN128}
 */
public class AltBn128FieldElement implements FieldElement {

    private final AltBn128Field field;
    private final byte[] representation;
    private final FieldFacade facade;

    /**
     * Creates a new {@link FieldElement}.
     * @param representation the byte array representation
     * @param field the {@link Field} that this instance will be an element of.
     */
    public AltBn128FieldElement(@NonNull final byte[] representation, @NonNull final AltBn128Field field) {
        this(representation, field, new FieldFacade(ArkBn254Adapter.getInstance()));
    }

    /**
     * Creates a new {@link FieldElement}.
     * @param representation the byte array representation
     * @param field the {@link Field} that this instance will be an element of.
     * @param facade the class implementing the high-level operations to handle FieldElements representations
     */
    AltBn128FieldElement(
            @NonNull final byte[] representation,
            @NonNull final AltBn128Field field,
            @NonNull final FieldFacade facade) {
        this.representation = Objects.requireNonNull(representation, "representation must not be null");
        this.field = field;
        this.facade = facade;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Field field() {
        return this.field;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FieldElement add(@NonNull final FieldElement other) {
        return new AltBn128FieldElement(
                facade.add(this.representation, expectOrThrow(AltBn128FieldElement.class, other).representation),
                field,
                facade);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FieldElement subtract(@NonNull final FieldElement other) {
        return new AltBn128FieldElement(
                facade.subtracts(this.representation, expectOrThrow(AltBn128FieldElement.class, other).representation),
                field,
                facade);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FieldElement multiply(@NonNull final FieldElement other) {
        return new AltBn128FieldElement(
                facade.multiply(this.representation, expectOrThrow(AltBn128FieldElement.class, other).representation),
                field,
                facade);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FieldElement power(final long exponent) {
        return new AltBn128FieldElement(facade.pow(this.representation, exponent), field, facade);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FieldElement inverse() {
        return new AltBn128FieldElement(facade.inverse(this.representation), field, facade);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BigInteger toBigInteger() {
        return ByteArrayUtils.fromLittleEndianBytes(representation);
    }

    /**
     * Returns the byte array representation of the field element
     * The representation is in {@link java.nio.ByteOrder#LITTLE_ENDIAN}
     * @return the byte array representation of the field element
     */
    @NonNull
    @Override
    public byte[] toBytes() {
        return this.representation.clone();
    }

    /**
     * Returns the internal byte[] of this element.
     * @implNote This has limited visibility as is only intended to be used internally in the library.
     * Users of the library are expected to get a copy of the array accessing the {@link AltBn128GroupElement#toBytes()} method.
     * @return the internal projective representation of this point
     */
    byte[] getRepresentation() {
        return representation;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof AltBn128FieldElement)) return false;
        if (this.representation.length != ((AltBn128FieldElement) obj).representation.length) return false;

        return Arrays.equals(this.representation, ((AltBn128FieldElement) obj).representation);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.representation);
    }
}
