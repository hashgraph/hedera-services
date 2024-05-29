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

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.tss.TssCommitment;
import com.swirlds.platform.hcm.api.tss.TssMultishareCiphertext;
import com.swirlds.platform.hcm.api.tss.TssProof;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;

/**
 * A TSS proof, as utilized by the Groth21 scheme.
 *
 * @param f   TODO
 * @param a
 * @param y
 * @param z_r
 * @param z_a
 */
public record Groth21Proof(
        @NonNull GroupElement f,
        @NonNull GroupElement a,
        @NonNull GroupElement y,
        @NonNull FieldElement z_r,
        @NonNull FieldElement z_a)
        implements TssProof {

    /**
     * Generates a Groth21 proof.
     *
     * @param random            a source of randomness
     * @param randomness        a list of random field elements to use in the proof
     * @param unencryptedShares a list of unencrypted shares
     * @return the Groth21 proof
     */
    public static Groth21Proof create(
            @NonNull final Random random,
            @NonNull final List<FieldElement> randomness,
            @NonNull final List<Groth21UnencryptedShare> unencryptedShares) {

        if (randomness.isEmpty()) {
            throw new IllegalArgumentException("Randomness must not be empty");
        }

        if (unencryptedShares.isEmpty()) {
            throw new IllegalArgumentException("Unencrypted shares must not be empty");
        }

        final Field field = randomness.getFirst().getField();
        final Group publicKeyGroup = unencryptedShares
                .getFirst()
                .shareClaim()
                .publicKey()
                .keyElement()
                .getGroup();

        final FieldElement x = field.randomElement(random); // obviously TODO

        final FieldElement alpha = field.randomElement(random);
        final FieldElement rho = field.randomElement(random);

        // TODO: are there any better names for these?
        final GroupElement f = publicKeyGroup.getGenerator().power(rho);
        final GroupElement a = publicKeyGroup.getGenerator().power(alpha);

        // TODO: is oneElement with multiply to accumulate correct here, or do we need to introduce a zeroElement?
        GroupElement y = publicKeyGroup.oneElement();
        for (final Groth21UnencryptedShare unencryptedShare : unencryptedShares) {
            final TssShareClaim shareClaim = unencryptedShare.shareClaim();
            final GroupElement publicKey = shareClaim.publicKey().keyElement();
            final FieldElement shareId = shareClaim.shareId().idElement();

            y = y.multiply(publicKey.power(x.power(shareId.toBigInteger())));
        }

        final FieldElement xPrime = field.randomElement(random); // obviously TODO

        final FieldElement combinedRandomness = combineRandomness(randomness);
        final FieldElement z_r = xPrime.multiply(combinedRandomness).add(rho);

        FieldElement combinedShares = field.zeroElement();
        for (final Groth21UnencryptedShare unencryptedShare : unencryptedShares) {
            final FieldElement indexSecret = unencryptedShare.shareElement();
            final FieldElement shareId = unencryptedShare.shareClaim().shareId().idElement();
            combinedShares = combinedShares.add(indexSecret.power(shareId.toBigInteger()));
        }

        final FieldElement z_a = alpha.add(xPrime.multiply(combinedShares));

        return new Groth21Proof(f, a, y, z_r, z_a);
    }

    /**
     * Combines randomness elements into a single element.
     *
     * @param randomness the randomness elements
     * @return the combined randomness element
     */
    private static FieldElement combineRandomness(@NonNull final List<FieldElement> randomness) {
        final Field field = randomness.getFirst().getField();
        FieldElement output = field.zeroElement();

        // TODO: is this `256` the same as the size of the elgamal cache?
        for (int i = 0; i < randomness.size(); i++) {
            output = output.add(
                    randomness.get(i).multiply(field.elementFromLong(256).power(BigInteger.valueOf(i))));
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(@NonNull final TssMultishareCiphertext ciphertext, @NonNull final TssCommitment commitment) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
