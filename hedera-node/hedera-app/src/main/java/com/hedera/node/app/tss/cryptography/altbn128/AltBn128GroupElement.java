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

import com.hedera.node.app.tss.cryptography.altbn128.facade.GroupFacade;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;

import static com.hedera.node.app.tss.cryptography.utils.ValidationUtils.expectOrThrow;

/**
 * The implementation of a {@link GroupElement}
 * for {@link com.hedera.node.app.tss.cryptography.pairings.api.curves.KnownCurves#ALT_BN128}
 */
public class AltBn128GroupElement implements GroupElement {
    private final AltBn128Group group;
    private final byte[] representation;
    private final GroupFacade facade;
    /**
     * Creates a new instance
     * @param group the group this element belongs to
     * @param representation the byte array representation of this element
     */
    public AltBn128GroupElement(@NonNull final AltBn128Group group, @NonNull final byte[] representation) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.representation = Objects.requireNonNull(representation, "innerRepresentation must not be null");
        this.facade = group.getFacade();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Group getGroup() {
        return group;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement multiply(@NonNull final FieldElement other) {
        return new AltBn128GroupElement(
                group,
                facade.scalarMul(
                        this.representation,
                        expectOrThrow(AltBn128FieldElement.class, other).toBytes()));
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException if other is not instance of {@link AltBn128GroupElement}
     */
    @NonNull
    @Override
    public GroupElement add(@NonNull final GroupElement other) {
        return new AltBn128GroupElement(
                group, facade.add(this.representation, isSameAltBn128GroupElement(this.group, other).representation));
    }

    /**
     * Checks if the received elements is the same subtype and belongs to the same group.
     * @param group expected group
     * @param other instance to check
     * @return  {@code other} instance cast to the expected subclass
     * @throws IllegalArgumentException if not.
     */
    static AltBn128GroupElement isSameAltBn128GroupElement(
            final @NonNull Group group, final @NonNull GroupElement other) {
        AltBn128GroupElement theOther = expectOrThrow(AltBn128GroupElement.class, other);
        if (theOther.getGroup() != group) {
            throw new IllegalArgumentException("Elements do not belong to the same group");
        }
        return theOther;
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Deprecated
    @NonNull
    @Override
    public GroupElement copy() {
        return new AltBn128GroupElement(group, toBytes());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] toBytes() {
        return this.representation.clone();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AltBn128GroupElement)) {
            return false;
        }
        if (this.group.getGroup() != ((AltBn128GroupElement) obj).group.getGroup()) return false;

        return Arrays.equals(this.representation, ((AltBn128GroupElement) obj).representation);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.representation);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.representation.length;
    }
}
