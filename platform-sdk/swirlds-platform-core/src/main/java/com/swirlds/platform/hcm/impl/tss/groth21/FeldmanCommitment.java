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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.tss.TssCommitment;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * A Feldman polynomial commitment.
 *
 * @param commitmentCoefficients the commitment coefficients. Each element in this list consists of the group generator,
 *                               raised to the power of a coefficient of the polynomial being committed to.
 */
public record FeldmanCommitment(@NonNull List<GroupElement> commitmentCoefficients) implements TssCommitment {
    /**
     * Creates a Feldman commitment.
     *
     * @param group      the group that elements of the commitment are in
     * @param polynomial the polynomial to commit to
     * @return the Feldman commitment
     */
    public static FeldmanCommitment create(@NonNull final Group group, @NonNull final DensePolynomial polynomial) {

        final GroupElement generator = group.getGenerator();

        final List<GroupElement> commitmentCoefficients = new ArrayList<>();
        for (final FieldElement polynomialCoefficient : polynomial.coefficients()) {
            commitmentCoefficients.add(generator.power(polynomialCoefficient));
        }

        return new FeldmanCommitment(commitmentCoefficients);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public GroupElement extractShareKeyMaterial(@NonNull final TssShareId shareId) {
        if (commitmentCoefficients.size() < 2) {
            throw new IllegalArgumentException("Coefficient commitments must have at least 2 elements");
        }

        final Group group = commitmentCoefficients.getFirst().getGroup();

        GroupElement publicKey = group.oneElement();
        for (int i = 0; i < commitmentCoefficients.size(); i++) {
            final GroupElement term = commitmentCoefficients.get(i);
            final FieldElement exponentiatedShareId = shareId.idElement().power(BigInteger.valueOf(i));

            publicKey = publicKey.multiply(term.power(exponentiatedShareId));
        }

        return publicKey;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GroupElement getTerm(final int index) {
        return commitmentCoefficients.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
