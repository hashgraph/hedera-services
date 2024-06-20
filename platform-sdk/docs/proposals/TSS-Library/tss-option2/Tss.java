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

package com.swirlds.tss.api;

import com.swirlds.signaturescheme.api.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * A Threshold Signature Scheme.
 * <p>
 * Contract of TSS:
 * <ul>
 *     <li>produce a public key for each share</li>
 *     <li>give the corresponding secret to the shareholder</li>
 * </ul>
 */
public interface Tss {
    /**
     * Generate a TSS message for a set of share claims, from a private share.
     *
     * @param random             a source of randomness
     * @param signatureSchema    the signature schema to use
     * @param pendingShareClaims the share claims that we should generate the message for
     * @param privateShare       the secret to use for generating new keys
     * @param threshold          the threshold for recovering the secret
     * @return the TSS message produced for the input share claims
     */
    @NonNull
    TssMessage generateTssMessage(
            @NonNull final Random random,
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final ShareClaims pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold);

    /**
     * Get the signature schema used by this TSS instance
     *
     * @return the signature schema
     */
    @NonNull
    SignatureSchema getSignatureSchema();

    /**
     * Compute a private share that belongs to this node.
     *
     * @param signatureSchema   the signature schema to use
     * @param shareId           the share ID owned by this node, for which the private share will be decrypted
     * @param elGamalPrivateKey the ElGamal private key of this node
     * @param tssMessages       the TSS messages to extract the private shares from
     * @param elGamalCache      the ElGamal cache
     * @param shareClaims       the share claims that the tss messages were created for
     * @param threshold         the threshold number of cipher texts required to decrypt the private share
     * @return the private share, or null if there aren't enough shares to meet the threshold
     */
    @Nullable
    public static TssPrivateShare decryptPrivateShare(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final TssShareId shareId,
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final List<TssMessage> tssMessages,
            @NonNull final ElGamalCache elGamalCache,
            @NonNull final ShareClaims shareClaims,
            final int threshold) {

        // check if there are enough messages to meet the required threshold
        if (tssMessages.size() < threshold) {
            return null;
        }

        final int shareIndex = shareClaims.getShareIdIndex(shareId);

        // decrypt the partial private shares from the cipher texts
        final List<TssPrivateShare> partialPrivateShares = new ArrayList<>();

        for (final TssMessage tssMessage : tssMessages) {
            final MultishareCiphertext multishareCiphertext = tssMessage.cipherText();
            final List<GroupElement> randomness = multishareCiphertext.chunkRandomness();
            final EncryptedShare encryptedShare =
                    multishareCiphertext.shareCiphertexts().get(shareIndex);

            partialPrivateShares.add(new TssPrivateShare(
                    shareId, encryptedShare.decryptPrivateKey(elGamalPrivateKey, elGamalCache, randomness)));
        }

        // aggregate the decrypted partial private shares, creating the actual private share
        return new TssPrivateShare(shareId, aggregatePrivateShares(signatureSchema, partialPrivateShares));
    }

    /**
     * Compute the public share for a specific share ID.
     *
     * @param signatureSchema the signature schema to use
     * @param shareId         the share ID to compute the public share for
     * @param tssMessages     the TSS messages to extract the public shares from
     * @param threshold       the threshold number of messages required to compute the public share
     * @return the public share, or null if there aren't enough messages to meet the threshold
     */
    @Nullable
    public static TssPublicShare computePublicShare(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final TssShareId shareId,
            @NonNull final List<TssMessage> tssMessages,
            final int threshold) {

        // check if there are enough TSS messages to meet the required threshold
        if (tssMessages.size() < threshold) {
            return null;
        }

        // each share in this partialShares list represents a public key obtained from a commitment
        // the share ID in each of these partial shares corresponds to the share ID that *CREATED* the commitment,
        // NOT to the share ID that the public key is for
        final List<TssPublicShare> partialShares = new ArrayList<>();

        for (final TssMessage tssMessage : tssMessages) {
            partialShares.add(new TssPublicShare(
                    tssMessage.shareId(),
                    new PairingPublicKey(
                            signatureSchema, tssMessage.commitment().extractShareKeyMaterial(shareId))));
        }

        return new TssPublicShare(shareId, aggregatePublicShares(partialShares));
    }

    /**
     * Aggregate a threshold number of {@link TssShareSignature}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of partial signatures meets the required
     * threshold. If the threshold is not met, the signature returned by this method will be invalid.
     *
     * @param partialSignatures the list of signatures to aggregate
     * @return the interpolated signature
     */
    @NonNull
    public static PairingSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures) {
        if (partialSignatures.isEmpty()) {
            throw new IllegalArgumentException("At least one element is required to compute an aggregate");
        }

        final SignatureSchema signatureSchema =
                partialSignatures.getFirst().signature().signatureSchema();

        final List<FieldElement> shareIds = partialSignatures.stream()
                .map(share -> share.shareId().idElement())
                .toList();
        final List<GroupElement> publicKeyElements = partialSignatures.stream()
                .map(share -> share.signature().signatureElement())
                .toList();

        return new PairingSignature(signatureSchema, aggregateGroupElements(shareIds, publicKeyElements));
    }

    /**
     * Aggregate a threshold number of {@link TssPublicShare}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of public shares meets the required threshold.
     * If the threshold is not met, the public key returned by this method will be invalid.
     * <p>
     * This method is used for two distinct purposes:
     * <ul>
     *     <li>Aggregating public shares to produce the Ledger ID</li>
     *     <li>Aggregating public shares derived from all commitments, to produce the public key for a given share</li>
     * </ul>
     *
     * @param publicShares the public shares to aggregate
     * @return the interpolated public key
     */
    @NonNull
    public static PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        if (publicShares.isEmpty()) {
            throw new IllegalArgumentException("At least one element is required to compute an aggregate");
        }

        final SignatureSchema signatureSchema =
                publicShares.getFirst().publicKey().signatureSchema();

        final List<FieldElement> shareIds =
                publicShares.stream().map(share -> share.shareId().idElement()).toList();
        final List<GroupElement> publicKeyElements = publicShares.stream()
                .map(share -> share.publicKey().keyElement())
                .toList();

        return new PairingPublicKey(signatureSchema, aggregateGroupElements(shareIds, publicKeyElements));
    }

    /**
     * Aggregate a threshold number of {@link TssPrivateShare}s.
     * <p>
     * It is the responsibility of the caller to ensure that the list of private shares meets the required threshold.
     * If the threshold is not met, the private key returned by this method will be invalid.
     *
     * @param signatureSchema the signature schema to use
     * @param privateShares   the private shares to aggregate
     * @return the aggregate private key
     */
    @NonNull
    public static PairingPrivateKey aggregatePrivateShares(
            @NonNull final SignatureSchema signatureSchema, @NonNull final List<TssPrivateShare> privateShares) {

        if (privateShares.isEmpty()) {
            throw new IllegalArgumentException("At least one private share is required to recover a secret");
        }

        final List<FieldElement> shareIds = new ArrayList<>();
        final List<FieldElement> privateKeys = new ArrayList<>();
        privateShares.forEach(share -> {
            shareIds.add(share.shareId().idElement());
            privateKeys.add(share.privateKey().secretElement());
        });

        if (shareIds.size() != Set.of(shareIds).size()) {
            throw new IllegalArgumentException("x-coordinates must be distinct");
        }

        final List<FieldElement> lagrangeCoefficients = new ArrayList<>();
        for (int i = 0; i < shareIds.size(); i++) {
            lagrangeCoefficients.add(computeLagrangeCoefficient(shareIds, i));
        }

        final Field field = shareIds.getFirst().getField();
        FieldElement sum = field.elementFromLong(0L);
        for (int i = 0; i < lagrangeCoefficients.size(); i++) {
            sum = sum.add(lagrangeCoefficients.get(i).multiply(privateKeys.get(i)));
        }

        return new PairingPrivateKey(signatureSchema, sum);
    }

    /**
     * Aggregate a number of group elements, using lagrange interpolation.
     *
     * @param shareIds a list of field elements representing share IDs
     * @param elements a list of group elements to aggregate
     * @return the aggregated group element
     */
    private static GroupElement aggregateGroupElements(
            @NonNull final List<FieldElement> shareIds, @NonNull final List<GroupElement> elements) {

        if (shareIds.isEmpty()) {
            throw new IllegalArgumentException("At least one element is required to compute an aggregate");
        }

        if (shareIds.size() != elements.size()) {
            throw new IllegalArgumentException("Mismatched share ID and element count");
        }

        final List<FieldElement> lagrangeCoefficients = new ArrayList<>();
        for (int i = 0; i < shareIds.size(); i++) {
            lagrangeCoefficients.add(computeLagrangeCoefficient(shareIds, i));
        }

        //         let public_key = λs.iter()
        //            .zip(dealt_share_pubkeys.iter())
        //            .fold(G::zero(), |acc, (&λ_i, &y_i)| { acc + y_i.mul(λ_i) });

        final GroupElementAggregator aggregator = new GroupElementAggregator();
        for (int i = 0; i < lagrangeCoefficients.size(); i++) {
            aggregator.add(elements.get(i).multiply(lagrangeCoefficients.get(i)));
        }

        return aggregator.compute();
    }

    /**
     * Compute the lagrange coefficient at a specific index.
     * <p>
     * The output of this method is the evaluation of the lagrange polynomial x = 0
     *
     * @param xCoordinates   the x-coordinates
     * @param indexToCompute the index to compute the lagrange coefficient for
     * @return the lagrange coefficient, which is the evaluation of the lagrange polynomial at x = 0
     */
    @NonNull
    private static FieldElement computeLagrangeCoefficient(
            @NonNull final List<FieldElement> xCoordinates, final int indexToCompute) {

        if (indexToCompute >= xCoordinates.size()) {
            throw new IllegalArgumentException("y-coordinate to compute must be within the range of x-coordinates");
        }

        final FieldElement xi = xCoordinates.get(indexToCompute);

        final Field field = xi.getField();
        final FieldElement zeroElement = field.elementFromLong(0L);
        final FieldElement oneElement = field.elementFromLong(1L);

        FieldElement numerator = oneElement;
        FieldElement denominator = oneElement;
        for (int j = 0; j < xCoordinates.size(); j++) {
            if (j != indexToCompute) {
                final FieldElement xj = xCoordinates.get(j);
                numerator = numerator.multiply(zeroElement.subtract(xj));
                denominator = denominator.multiply(xi.subtract(xj));
            }
        }

        final FieldElement denominatorInverse = denominator.multiply(zeroElement.subtract(oneElement));

        return numerator.multiply(denominatorInverse);
    }
}
