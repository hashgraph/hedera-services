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

import com.hedera.node.app.tss.cryptography.altbn128.adapter.jni.ArkBn254Adapter;
import com.hedera.node.app.tss.cryptography.altbn128.facade.GroupFacade;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.api.PairingFriendlyCurve;
import com.hedera.node.app.tss.cryptography.utils.HashUtils;
import com.hedera.node.app.tss.cryptography.utils.HashUtils.HashCalculator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The implementation of the two {@link Group} of {@link com.hedera.node.app.tss.cryptography.pairings.api.curves.KnownCurves#ALT_BN128}
 */
public class AltBn128Group implements Group {
    /** String ID to use for obtaining the digest algorithm */
    private static final String KECCAK_256 = "Keccak-256";
    /** The number of times to rehash in {@link #hashToCurve(byte[])} */
    private static final int HASH_RETRIES = 255;

    private final GroupFacade facade;
    private final AltBN128CurveGroup group;

    static {
        // add provider only if it's not in the JVM
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Creates an instance of a {@link GroupFacade} for this implementation.
     * @param group  the actual group represented by this instance
     */
    public AltBn128Group(final @NonNull AltBN128CurveGroup group) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.facade = new GroupFacade(
                group.getId(),
                ArkBn254Adapter.getInstance(),
                ArkBn254Adapter.getInstance().fieldElementsSize());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PairingFriendlyCurve getPairingFriendlyCurve() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement generator() {
        return new AltBn128GroupElement(this, facade.generator());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement zero() {
        return new AltBn128GroupElement(this, facade.zero());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement random(@NonNull final byte[] seed) {
        return new AltBn128GroupElement(this, facade.fromSeed(seed));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement hashToCurve(@NonNull final byte[] input) {
        HashCalculator calculator = HashUtils.getHashCalculator(KECCAK_256);
        // hash the input and try to find a valid group element
        // hash the hash until we find a valid group element
        byte[] candidate = input;
        for (int i = 0; i < HASH_RETRIES; i++) {
            calculator.append(candidate);
            candidate = calculator.hash();
            final byte[] element = facade.fromXCoordinate(candidate);
            if (element != null) {
                return new AltBn128GroupElement(this, element);
            }
        }
        throw new AltBn128Exception("Could not find a valid group element after %d tries".formatted(HASH_RETRIES));
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException if any of the elements is null or not an instance of {@link AltBn128GroupElement}
     */
    @NonNull
    @Override
    public GroupElement add(@NonNull final Collection<GroupElement> elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        List<AltBn128GroupElement> elems = elements.stream()
                .map(e -> AltBn128GroupElement.isSameAltBn128GroupElement(this, e))
                .toList();
        final byte[][] all = new byte[elems.size()][];
        for (int i = 0; i < elems.size(); i++) {
            all[i] = elems.get(i).getRepresentation();
        }
        return new AltBn128GroupElement(this, facade.batchAdd(all));
    }

    /**
     * Multiplies a list of scalar values for the generator point of the group
     *
     *
     * @param elements the scalar elements to multiply the generator
     * @return same size list of points that are the generator point of this curve times the scalar in the same index
     * @throws NullPointerException if the elements is null
     * @throws IllegalArgumentException if the bytes are n
     * @throws AltBn128Exception in case of error.
     *
     */
    public List<GroupElement> batchMultiply(@NonNull final Collection<FieldElement> elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        List<AltBn128FieldElement> elems;
        try {
            elems = elements.stream().map(AltBn128FieldElement.class::cast).toList();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("elements must implement AltBn128FieldElement");
        }

        final byte[][] all = new byte[elems.size()][];
        for (int i = 0; i < elems.size(); i++) {
            all[i] = elems.get(i).getRepresentation();
        }
        final byte[][] groupElements = facade.batchMultiply(all);

        return Arrays.stream(groupElements)
                .map(rep -> (GroupElement) new AltBn128GroupElement(this, rep))
                .toList();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final AltBn128Group that)) {
            return false;
        }
        return group == that.group;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(group);
    }

    /**
     * Creates a group element from its serialized encoding, validating if the point is in the curve.
     *
     * @throws NullPointerException if the bytes is null
     * @throws IllegalArgumentException if the bytes is of invalid size or the point does not belong to the curve
     * @throws AltBn128Exception in case of error.
     */
    @NonNull
    @Override
    public GroupElement fromBytes(@NonNull final byte[] bytes) {
        return new AltBn128GroupElement(this, facade.fromBytes(bytes));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int seedSize() {
        return facade.randomSeedSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int elementSize() {
        return facade.size();
    }

    /**
     * Returns the facade.
     * Internal method
     * @return the facade
     */
    GroupFacade getFacade() {
        return facade;
    }

    /**
     * Returns the curve group.
     * Internal method
     * @return the curve group.
     */
    AltBN128CurveGroup getGroup() {
        return group;
    }
}
