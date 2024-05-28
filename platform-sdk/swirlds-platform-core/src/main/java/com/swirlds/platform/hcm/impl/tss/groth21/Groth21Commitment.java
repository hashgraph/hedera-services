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

        final Group group = coefficientCommitments.getFirst().getGroup();

        GroupElement publicKey = group.oneElement();
        for (int i = 0; i < coefficientCommitments.size(); i++) {
            final GroupElement term = coefficientCommitments.get(i);
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
