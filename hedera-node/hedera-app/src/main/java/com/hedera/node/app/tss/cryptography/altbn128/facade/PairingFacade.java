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

package com.hedera.node.app.tss.cryptography.altbn128.facade;

import static com.hedera.node.app.tss.cryptography.utils.ValidationUtils.validateSize;

import com.hedera.node.app.tss.cryptography.altbn128.AltBN128CurveGroup;
import com.hedera.node.app.tss.cryptography.altbn128.AltBn128Exception;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.GroupElementsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.PairingsLibraryAdapter;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.jni.ArkBn254Adapter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This class acts as a facade that simplifies the interaction for operating specifically with the Pairings operation for alt-bn-128
 *  It abstracts the complexities of dealing with return codes and input and output parameters
 *  providing a higher-level interface easier to interact with from Java.
 **/
public class PairingFacade {

    /** The underlying library adapter */
    private final PairingsLibraryAdapter adapter;
    /** The occupied size in bytes of the GroupElement representations */
    private final int g1Size;
    /** The occupied size in bytes of the GroupElement representations */
    private final int g2Size;

    /**
     * Creates an instance of this facade.
     * @param adapter the adapter containing the underlying logic.
     */
    public PairingFacade(@NonNull final ArkBn254Adapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.g1Size = adapter.groupElementsSize(AltBN128CurveGroup.GROUP1.ordinal());
        this.g2Size = adapter.groupElementsSize(AltBN128CurveGroup.GROUP2.ordinal());
    }

    /**
     * Checks if two Group2 points are equal.
     * @param point1 the first point of the first pair to validate. Must be from Group1.
     * @param point2 the second point of the first pair to validate. Must be from Group2.
     * @param point3 the first point of the second pair to validate. Must be from Group1.
     * @param point4 the second point of the second pair to validate. Must be from Group2.
     * @return true if points are equal, false otherwise.
     * @throws AltBn128Exception in case of error.
     */
    public boolean validatePairings(
            @NonNull final byte[] point1,
            @NonNull final byte[] point2,
            @NonNull final byte[] point3,
            @NonNull final byte[] point4) {
        validateSize(point1, g1Size, "point1 must belong to group1");
        validateSize(point3, g1Size, "point3 must belong to group1");
        validateSize(point2, g2Size, "point2 must belong to group2");
        validateSize(point4, g2Size, "point4 must belong to group2");

        final int result = adapter.pairingsEquals(point1, point2, point3, point4);
        if (result < GroupElementsLibraryAdapter.SUCCESS) {
            throw new AltBn128Exception(result, "pairingsEquals");
        }
        return result == 1;
    }
}
