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
import com.hedera.node.app.tss.cryptography.altbn128.facade.PairingFacade;
import com.hedera.node.app.tss.cryptography.pairings.api.BilinearPairing;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

import static com.hedera.node.app.tss.cryptography.utils.ValidationUtils.expectOrThrow;

/**
 * Represents a bilinear pairing operation for Alt-bn-128 curve.
 */
public class AltBn128BilinearPairing implements BilinearPairing {
    private final AltBn128GroupElement first;
    private final AltBn128GroupElement second;

    /**
     * Crates a AltBn128BilinearPairing instance
     * @param first first element of this paring
     * @param second second element of this pairing
     */
    public AltBn128BilinearPairing(@NonNull final GroupElement first, @NonNull final GroupElement second) {
        this.first = expectOrThrow(AltBn128GroupElement.class, first);
        this.second = expectOrThrow(AltBn128GroupElement.class, second);
        if (first.getGroup() == second.getGroup()) {
            throw new IllegalArgumentException("first and second groups must not be the same");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AltBn128GroupElement first() {
        return first;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AltBn128GroupElement second() {
        return second;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean compare(final @NonNull BilinearPairing pairing) {
        final AltBn128BilinearPairing other = expectOrThrow(AltBn128BilinearPairing.class, pairing);
        final AltBn128GroupElement point1 =
                ((AltBn128Group) first.getGroup()).getGroup() == AltBN128CurveGroup.GROUP1 ? first : second;
        final AltBn128GroupElement point2 = point1 == first ? second : first;
        final AltBn128GroupElement point3 =
                ((AltBn128Group) (other.first()).getGroup()).getGroup() == AltBN128CurveGroup.GROUP1
                        ? other.first()
                        : other.second();
        final AltBn128GroupElement point4 = point3 == other.first() ? other.second() : other.first();

        final ArkBn254Adapter instance = ArkBn254Adapter.getInstance();
        return new PairingFacade(instance)
                .validatePairings(
                        point1.getRepresentation(),
                        point2.getRepresentation(),
                        point3.getRepresentation(),
                        point4.getRepresentation());
    }
}
