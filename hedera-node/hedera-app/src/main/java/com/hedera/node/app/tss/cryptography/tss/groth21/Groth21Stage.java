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

package com.hedera.node.app.tss.cryptography.tss.groth21;

import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.EcPolynomial;
import com.hedera.node.app.tss.cryptography.pairings.extensions.FiniteFieldPolynomial;
import com.hedera.node.app.tss.cryptography.tss.api.TssMessage;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssPublicShare;
import com.hedera.node.app.tss.cryptography.tss.extensions.ShamirUtils;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.CiphertextTable;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.CombinedCiphertext;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.ElGamalUtils;
import com.hedera.node.app.tss.cryptography.tss.extensions.nizk.NizkProof;
import com.hedera.node.app.tss.cryptography.tss.extensions.nizk.NizkStatement;
import com.hedera.node.app.tss.cryptography.tss.extensions.nizk.NizkWitness;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * All common behaviour for the between the stages implementations of TSS.
 * Contains all common code for implementing the {@link com.hedera.node.app.tss.cryptography.tss.api.TssServiceGenesisStage}
 * or {@link com.hedera.node.app.tss.cryptography.tss.api.TssServiceRekeyStage}
 */
public abstract class Groth21Stage {
    /**
     * defines which elliptic curve is used in the protocol, and how it's used
     */
    protected final SignatureSchema signatureSchema;
    /**
     * a random number generator
     */
    protected final Random random;

    /**
     * A Groth21Stage
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     */
    protected Groth21Stage(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        this.signatureSchema = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Cast the message to the instance this service will work.
     *
     * @param tssMessage the tssMessage to convert
     * @return a cast version of tssMessage
     * @throws IllegalArgumentException if it is not the valid instance of the message
     */
    @NonNull
    protected static Groth21Message fromTssMessage(@NonNull final TssMessage tssMessage) {
        if (!(tssMessage instanceof Groth21Message))
            throw new IllegalArgumentException(
                    "invalid message type: " + tssMessage.getClass().getSimpleName());
        return (Groth21Message) tssMessage;
    }

    /**
     * Cast the messages to the instance this service will work with.
     *
     * @param tssMessages the list of tssMessage to convert
     * @return a cast version of tssMessage
     * @throws IllegalArgumentException if it is not the valid instance of the message
     * @throws NullPointerException if the list is null
     */
    protected static List<Groth21Message> fromTssMessages(@NonNull final List<TssMessage> tssMessages) {
        return Objects.requireNonNull(tssMessages, "tssMessages must not be null").stream()
                .map(Groth21Stage::fromTssMessage)
                .toList();
    }

    /**
     * Generates a TssMessage from a participantDirectory and a generatingShare
     *
     * @param participantDirectory the candidate tss directory
     * @param generatingShare the secret to redistribute
     * @return a {@link TssMessage} for this share.
     */
    @NonNull
    public TssMessage generateTssMessage(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final TssPrivateShare generatingShare) {

        final List<Integer> receivingShareIds = participantDirectory.getShareIds();
        final FieldElement secret = generatingShare.privateKey().element();

        // First, crate a polynomial of degree d = threshold -1 so that threshold number of points can recover this
        // polynomial.
        // The value in the free coefficient is the secret that we want to share.
        final FiniteFieldPolynomial finiteFieldPolynomial =
                ShamirUtils.interpolationPolynomial(random, secret, participantDirectory.getThreshold() - 1);
        // The secrets we will end up sharing are the result of evaluating the polynomial with x=
        // receiving-share-participantId
        final List<FieldElement> secrets =
                receivingShareIds.stream().map(finiteFieldPolynomial::evaluate).toList();
        // Generating some shared entropy for ElGamal encryption algorithm. The randomness is reused for efficiency.
        final List<FieldElement> elGamalRandomness = ElGamalUtils.generateEntropy(
                random, signatureSchema.getPairingFriendlyCurve().field().elementSize(), signatureSchema);
        // This ciphertextTable contains the secrets encrypted for each receiver using the shared randomness and each
        // receiver tssEncryptionKey.
        final CiphertextTable ciphertextTable =
                ElGamalUtils.ciphertextTable(signatureSchema, elGamalRandomness, participantDirectory, secrets);

        // Zk proof: Create a collapsed representation of the cipherTable that can be used for a zk proof.
        final CombinedCiphertext elGamalCombinedCipherText = ciphertextTable.combine(
                signatureSchema.getPairingFriendlyCurve().field().fromLong(ElGamalUtils.TOTAL_NUMBER_OF_ELEMENTS));
        // Zk proof: Create a Feldman polynomial commitment. This allows to validate that the points belong to the
        // polynomial without revealing the polynomial.
        final EcPolynomial commitment =
                ShamirUtils.feldmanCommitment(signatureSchema.getPublicKeyGroup(), finiteFieldPolynomial);
        // Zk proof: Creating the public statement
        final NizkStatement nizkStatement =
                new NizkStatement(receivingShareIds, participantDirectory, commitment, elGamalCombinedCipherText);
        // Zk proof: Creating the private witness
        final NizkWitness nizkWitness = NizkWitness.create(elGamalRandomness, secrets);
        // Zk proof: Creating the private witness
        final NizkProof proof = NizkProof.prove(signatureSchema, random, nizkStatement, nizkWitness);
        return new Groth21Message(
                TssMessage.MESSAGE_CURRENT_VERSION,
                signatureSchema,
                generatingShare.shareId(),
                ciphertextTable,
                commitment,
                proof);
    }

    /**
     * Allows verification of the message against the zk proof and the previous public shares if sent.
     * @param tssTargetParticipantDirectory the directory
     * @param previousPublicShares The sorted list by shareId of the previous TssPublicShare. optional parameter.
     * @param tssMessage the message to verify
     * @return if the message is valid.
     */
    public boolean verifyTssMessage(
            @NonNull final TssParticipantDirectory tssTargetParticipantDirectory,
            @Nullable final List<TssPublicShare> previousPublicShares,
            @NonNull final TssMessage tssMessage) {
        final Groth21Message message = fromTssMessage(tssMessage);

        if (message.version() != TssMessage.MESSAGE_CURRENT_VERSION) {
            return false;
        }
        if (!signatureSchema.equals(message.signatureSchema())) {
            return false;
        }

        if (previousPublicShares != null) {
            final int shareId = message.generatingShare();
            if (shareId < 1 || shareId > previousPublicShares.size()) {
                return false;
            }
            final BlsPublicKey pk = previousPublicShares.get(shareId - 1).publicKey();
            if (!pk.element()
                    .equals(message.polynomialCommitment().coefficients().getFirst())) {
                return false;
            }
        }

        final CombinedCiphertext combinedCipher = message.cipherTable()
                .combine(signatureSchema
                        .getPairingFriendlyCurve()
                        .field()
                        .fromLong(ElGamalUtils.TOTAL_NUMBER_OF_ELEMENTS));
        // Zk proof: Creating the public statement
        final NizkStatement nizkStatement = new NizkStatement(
                tssTargetParticipantDirectory.getShareIds(),
                tssTargetParticipantDirectory,
                message.polynomialCommitment(),
                combinedCipher);

        return message.proof().verify(signatureSchema, nizkStatement);
    }
}
