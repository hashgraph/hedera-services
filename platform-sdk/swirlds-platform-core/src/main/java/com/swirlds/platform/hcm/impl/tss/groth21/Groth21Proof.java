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
import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.tss.ShareClaims;
import com.swirlds.platform.hcm.api.tss.TssProof;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
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
            @NonNull final List<UnencryptedShare> unencryptedShares) {

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

        final FieldElement x = field.elementFromLong(42L); // obviously TODO

        final FieldElement alpha = field.randomElement(random);
        final FieldElement rho = field.randomElement(random);

        // TODO: are there any better names for these?
        final GroupElement f = publicKeyGroup.getGenerator().multiply(rho);
        final GroupElement a = publicKeyGroup.getGenerator().multiply(alpha);

        //     // compute Y = Π_{i=1}^{n} (y_i)^x^i
        //    let inner = statement.public_keys
        //        .iter()
        //        .zip(statement.ids.iter())
        //        .fold(G::Affine::zero(), |acc, (y_i, &id_i)| {
        //        acc.add(y_i.mul(&x.pow(id_i.into_bigint())).into_affine()).into_affine()
        //    });
        //    let Y = inner.mul(rho).add(A).into_affine();

        GroupElement y = publicKeyGroup.zeroElement();
        for (final UnencryptedShare unencryptedShare : unencryptedShares) {
            final TssShareClaim shareClaim = unencryptedShare.shareClaim();
            final GroupElement publicKey = shareClaim.publicKey().keyElement();
            final FieldElement shareId = shareClaim.shareId().idElement();

            y = y.add(publicKey.multiply(x.power(shareId.toBigInteger())));
        }

        final FieldElement xPrime = field.elementFromLong(86L); // obviously TODO

        final FieldElement combinedRandomness = combineFieldRandomness(randomness);
        final FieldElement z_r = xPrime.multiply(combinedRandomness).add(rho);

        FieldElement combinedShares = field.zeroElement();
        for (final UnencryptedShare unencryptedShare : unencryptedShares) {
            final FieldElement indexSecret = unencryptedShare.shareElement();
            final FieldElement shareId = unencryptedShare.shareClaim().shareId().idElement();
            combinedShares = combinedShares.add(indexSecret.power(shareId.toBigInteger()));
        }

        final FieldElement z_a = alpha.add(xPrime.multiply(combinedShares));

        return new Groth21Proof(f, a, y, z_r, z_a);
    }

    /**
     * Combines randomness field elements into a single field element.
     *
     * @param fieldRandomness the randomness field elements
     * @return the combined randomness element
     */
    private static FieldElement combineFieldRandomness(@NonNull final List<FieldElement> fieldRandomness) {
        final Field field = fieldRandomness.getFirst().getField();
        FieldElement output = field.zeroElement();

        // TODO: is this `256` the same as the size of the elgamal cache?
        for (int i = 0; i < fieldRandomness.size(); i++) {
            output = output.add(
                    fieldRandomness.get(i).multiply(field.elementFromLong(256).power(BigInteger.valueOf(i))));
        }

        return output;
    }

    /**
     * Collapses a list of group elements into a single group element. // TODO: is this a good description?
     *
     * @param groupElements the group elements to collapse
     * @return the collapsed group element
     */
    private static GroupElement collapseGroupElements(@NonNull final List<GroupElement> groupElements) {
        final Group group = groupElements.getFirst().getGroup();
        final Field field = group.getPairing().getField();

        //         let c1 = ctxt.c1
        //            .iter().enumerate().fold(
        //                G::Affine::zero(),
        //                |acc, (j, c1_j)|
        //                    acc.add(c1_j.mul(&G::ScalarField::from(256u64).pow([j as u64])).into_affine())
        //                .into_affine()
        //            );

        GroupElement output = group.zeroElement();
        for (int i = 0; i < groupElements.size(); i++) {
            final GroupElement randomElement = groupElements.get(i);
            // TODO: is this `256` the same as the size of the elgamal cache?
            output =
                    output.add(randomElement.multiply(field.elementFromLong(256).power(BigInteger.valueOf(i))));
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(
            @NonNull final MultishareCiphertext ciphertext,
            @NonNull final FeldmanCommitment commitment,
            @NonNull final ShareClaims pendingShareClaims) {
        final Field field = z_r.getField();
        final Group group = f.getGroup();

        final List<TssShareId> shareIds = new ArrayList<>();
        final List<PairingPublicKey> sharePublicKeys = new ArrayList<>();
        for (final TssShareClaim shareClaim : pendingShareClaims.getClaims()) {
            shareIds.add(shareClaim.shareId());
            sharePublicKeys.add(shareClaim.publicKey());
        }

        //     // compute x := RO(instance)
        //    let x = ScalarField::<G>::from(42u64); // obviously TODO
        //    // compute x' := RO(x, F, A, Y)
        //    let x_prime = ScalarField::<G>::from(86u64); // obviously TODO

        final FieldElement x = field.elementFromLong(42L); // obviously TODO
        final FieldElement xPrime = field.elementFromLong(86L); // obviously TODO

        //
        //    // check R ^ x' . F = g ^ z_r
        //    let lhs = statement.ciphertext_rand.mul(&x_prime).add(&proof.F).into_affine();
        //    let rhs = G::generator().mul(&proof.z_r).into_affine();
        //    if lhs != rhs { return false; }

        final GroupElement lhsRandomness = collapseGroupElements(ciphertext.chunkRandomness())
                .multiply(xPrime)
                .add(f);
        final GroupElement rhsRandomness = group.getGenerator().multiply(z_a);
        if (!lhsRandomness.equals(rhsRandomness)) {
            return false;
        }

        //
        //    // compute product of feldman commitments raised to the power of x^i
        //    let inner = statement.polynomial_commitment
        //        .iter()
        //        .enumerate()
        //        .fold(G::Affine::zero(), |acc, (k, A_k)| {
        //            acc.add(
        //                A_k.mul(
        //                    statement.ids
        //                    .iter()
        //                    .fold(ScalarField::<G>::zero(), |acc, id_i| {
        //                        acc + id_i.pow([k as u64]) * x.pow(id_i.into_bigint())
        //                    })
        //                ).into_affine()
        //            ).into_affine()
        //        });

        GroupElement foldedCommitment = group.zeroElement();
        for (int coefficientIndex = 0;
                coefficientIndex < commitment.commitmentCoefficients().size();
                coefficientIndex++) {

            FieldElement foldedShareIds = field.zeroElement();
            for (final TssShareId shareId : shareIds) {
                final FieldElement idElement = shareId.idElement();
                foldedShareIds = foldedShareIds.add(idElement
                        .power(BigInteger.valueOf(coefficientIndex))
                        .multiply(x.power(idElement.toBigInteger())));
            }

            final GroupElement commitmentElement =
                    commitment.commitmentCoefficients().get(coefficientIndex);
            foldedCommitment = foldedCommitment.add(commitmentElement.multiply(foldedShareIds));
        }

        //    let lhs = inner.mul(&x_prime).add(&proof.A).into_affine();
        //    let rhs = G::generator().mul(&proof.z_a).into_affine();
        //    if lhs != rhs { return false; }

        final GroupElement lhsCommitment = foldedCommitment.multiply(xPrime).add(a);
        final GroupElement rhsCommitment = group.getGenerator().multiply(z_a);
        if (!lhsCommitment.equals(rhsCommitment)) {
            return false;
        }

        //     let inner = statement.ciphertext_values
        //        .iter()
        //        .zip(statement.ids.iter())
        //        .fold(G::Affine::zero(), |acc, (C_i, id_i)| {
        //            acc.add(C_i.mul(x.pow(id_i.into_bigint())).into_affine()).into_affine()
        //        });
        //    let lhs = inner.mul(&x_prime).add(&proof.Y).into_affine();

        final List<GroupElement> collapsedShares = new ArrayList<>();
        for (final EncryptedShare shareCiphertext : ciphertext.shareCiphertexts()) {
            collapsedShares.add(collapseGroupElements(shareCiphertext.ciphertextElements()));
        }

        GroupElement sharesLhsInner = group.zeroElement();
        for (int i = 0; i < collapsedShares.size(); i++) {
            final GroupElement collapsedShare = collapsedShares.get(i);
            final FieldElement shareId = shareIds.get(i).idElement();
            sharesLhsInner = sharesLhsInner.add(collapsedShare.multiply(x.power(shareId.toBigInteger())));
        }

        final GroupElement lhsShares = sharesLhsInner.multiply(xPrime).add(y);

        //    let inner = statement.public_keys
        //        .iter()
        //        .zip(statement.ids.iter())
        //        .fold(G::Affine::zero(), |acc, (y_i, id_i)| {
        //            acc.add(y_i.mul(proof.z_r * x.pow(id_i.into_bigint())).into_affine()).into_affine()
        //        });

        GroupElement shareRhsInner = group.zeroElement();
        for (int i = 0; i < sharePublicKeys.size(); i++) {
            final GroupElement publicKey = sharePublicKeys.get(i).keyElement();
            final FieldElement shareId = shareIds.get(i).idElement();
            shareRhsInner = shareRhsInner.add(publicKey.multiply(z_r.multiply(x.power(shareId.toBigInteger()))));
        }

        //    let rhs = inner.add(G::generator().mul(proof.z_a)).into_affine();
        //    if lhs != rhs { return false; }

        final GroupElement rhsShares = shareRhsInner.add(group.getGenerator().multiply(z_a));

        return lhsShares.equals(rhsShares);
    }
}
