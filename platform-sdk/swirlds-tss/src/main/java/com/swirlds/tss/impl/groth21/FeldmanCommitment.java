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

package com.swirlds.tss.impl.groth21;

import com.swirlds.pairings.api.FieldElement;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.api.GroupElement;
import com.swirlds.tss.api.TssShareId;
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
public record FeldmanCommitment(@NonNull List<GroupElement> commitmentCoefficients) {
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
            commitmentCoefficients.add(generator.multiply(polynomialCoefficient));
        }

        return new FeldmanCommitment(commitmentCoefficients);
    }

    /**
     * Extract the public key material for the given share ID.
     * <p>
     * The key material returned by this method will be aggregated with the public keys from all other commitments for
     * the same share, and the result will be the final public key for that share.
     *
     * @param shareId the share ID of the public key to extract
     * @return the public key extracted from this commitment
     */
    @NonNull
    public GroupElement extractShareKeyMaterial(@NonNull final TssShareId shareId) {
        if (commitmentCoefficients.size() < 2) {
            throw new IllegalArgumentException("Coefficient commitments must have at least 2 elements");
        }

        final Group group = commitmentCoefficients.getFirst().getGroup();

        //         for msg in dkg_messages.iter() {
        //            // compute powers of share_id
        //            let xs = (0..msg.commitment.len()).map(|i| share_id.pow([i as u64])).collect::<Vec<ShareId<G>>>();
        //            let share_of_share_pubkey = msg.commitment.iter().zip(xs.iter()).fold(G::zero(), |acc, (&a_i,
        // &x_i)| { acc + a_i.mul(x_i) });
        //            dealt_share_pubkeys.push(share_of_share_pubkey);
        //        }

        GroupElement publicKey = group.zeroElement();
        for (int i = 0; i < commitmentCoefficients.size(); i++) {
            final GroupElement term = commitmentCoefficients.get(i);
            final FieldElement exponentiatedShareId = shareId.idElement().power(BigInteger.valueOf(i));

            publicKey = publicKey.add(term.multiply(exponentiatedShareId));
        }

        return publicKey;
    }

    /**
     * Get the byte array representation of this commitment.
     *
     * @return the byte array representation of this commitment
     */
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
