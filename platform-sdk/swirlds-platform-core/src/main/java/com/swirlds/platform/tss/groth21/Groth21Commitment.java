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

package com.swirlds.platform.tss.groth21;

import com.swirlds.platform.tss.TssCommitment;
import com.swirlds.platform.tss.TssShareId;
import com.swirlds.platform.tss.pairings.FieldElement;
import com.swirlds.platform.tss.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;

/**
 * A TSS commitment, as utilized by the Groth21 scheme.
 *
 * @param coefficientCommitments TODO
 */
public record Groth21Commitment(@NonNull List<GroupElement> coefficientCommitments) implements TssCommitment {
    /**
     * {@inheritDoc}
     */
    @NonNull
    public GroupElement extractPublicKey(@NonNull final TssShareId shareId) {
        if (coefficientCommitments.size() < 2) {
            throw new IllegalArgumentException("Coefficient commitments must have at least 2 elements");
        }

        GroupElement product = null;
        for (int i = 0; i < coefficientCommitments.size(); i++) {
            final GroupElement term = coefficientCommitments.get(i);
            final FieldElement exponentiatedShareId = shareId.id().power(BigInteger.valueOf(i));

            final GroupElement power = term.power(exponentiatedShareId);

            if (product == null) {
                product = power;
            } else {
                product = product.multiply(power);
            }
        }

        return product;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement getTerm(final int index) {
        return coefficientCommitments.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
