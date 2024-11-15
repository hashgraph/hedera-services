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

import com.hedera.node.app.tss.cryptography.pairings.api.BilinearPairing;
import com.hedera.node.app.tss.cryptography.pairings.api.Curve;
import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.api.PairingFriendlyCurve;
import com.hedera.node.app.tss.cryptography.pairings.api.curves.KnownCurves;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a bilinear pairing friendly curve alt-bn-128 and its component
 *  {@code G₁}, {@code G₂} and {@code Fq} (Finite Field).
 */
public class AltBn128 implements PairingFriendlyCurve {
    /**
     * The finite field
     */
    private final AltBn128Field field = new AltBn128Field();
    /**
     * First group of the curve
     */
    private final AltBn128Group group1 = new AltBn128Group(AltBN128CurveGroup.GROUP1);
    /**
     * Second group of the curve
     */
    private final AltBn128Group group2 = new AltBn128Group(AltBN128CurveGroup.GROUP2);

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Curve curve() {
        return KnownCurves.ALT_BN128;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Field field() {
        return field;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Group group1() {
        return group1;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Group group2() {
        return group2;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Group getOtherGroup(@NonNull final Group group) {
        AltBn128Group other = expectOrThrow(AltBn128Group.class, group);
        return this.group1.getGroup() == other.getGroup() ? group2 : group1;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BilinearPairing pairingBetween(@NonNull final GroupElement element1, @NonNull final GroupElement element2) {
        return new AltBn128BilinearPairing(element1, element2);
    }
}
