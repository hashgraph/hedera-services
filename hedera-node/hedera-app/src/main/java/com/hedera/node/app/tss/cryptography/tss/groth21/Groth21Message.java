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

import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.EcPolynomial;
import com.hedera.node.app.tss.cryptography.tss.api.TssMessage;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.CipherText;
import com.hedera.node.app.tss.cryptography.tss.extensions.elgamal.CiphertextTable;
import com.hedera.node.app.tss.cryptography.tss.extensions.nizk.NizkProof;
import com.hedera.node.app.tss.cryptography.utils.ByteArrayUtils.Deserializer;
import com.hedera.node.app.tss.cryptography.utils.ByteArrayUtils.Serializer;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A message sent as part of either genesis keying, or rekeying.
 * @param version supported version of the message
 * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
 * @param generatingShare share generating the message
 * @param cipherTable an ElGamal cipher per receiving share
 * @param polynomialCommitment a FeldmanCommitment
 * @param proof a Nizk proof
 */
public record Groth21Message(
        int version,
        @NonNull SignatureSchema signatureSchema,
        @NonNull Integer generatingShare,
        @NonNull CiphertextTable cipherTable,
        @NonNull EcPolynomial polynomialCommitment,
        @NonNull NizkProof proof)
        implements TssMessage {

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public byte[] toBytes() {
        final Serializer serializer = new Serializer()
                .put(version)
                .put(signatureSchema.toByte())
                .put(generatingShare)
                .putListSameSize(cipherTable.sharedRandomness(), GroupElement::toBytes);
        for (var cipherText : cipherTable.shareCiphertexts()) {
            serializer.putListSameSize(cipherText.cipherText(), GroupElement::toBytes);
        }
        return serializer
                .putListSameSize(polynomialCommitment.coefficients(), GroupElement::toBytes)
                .put(proof.f()::toBytes)
                .put(proof.a()::toBytes)
                .put(proof.y()::toBytes)
                .put(proof.zR()::toBytes)
                .put(proof.zA()::toBytes)
                .toBytes();
    }

    /**
     * Reads a {@link Groth21Message} from its serialized form following the specs in {@link TssMessage#toBytes()}
     *
     * @param message the byte array representation of the message
     * @param tssParticipantDirectory the candidate tss directory
     * @param expectedSchema the signatureSchema expected
     * @return a Groth21Message instance
     * @throws IllegalStateException if the message cannot be read
     */
    @NonNull
    public static Groth21Message fromBytes(
            @NonNull final byte[] message,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final SignatureSchema expectedSchema) {
        final Deserializer deserializer = new Deserializer(Objects.requireNonNull(message, "message must not be null"));
        Objects.requireNonNull(tssParticipantDirectory, "tssParticipantDirectory must not be null");
        Objects.requireNonNull(expectedSchema, "expectedSchema must not be null");
        final int fieldElementSize =
                expectedSchema.getPairingFriendlyCurve().field().elementSize();
        final int groupElementSize = expectedSchema.getPublicKeyGroup().elementSize();
        final Function<byte[], FieldElement> fieldElementFunction =
                expectedSchema.getPairingFriendlyCurve().field()::fromBytes;
        final Function<byte[], GroupElement> groupElementFunction = expectedSchema.getPublicKeyGroup()::fromBytes;
        final int totalShares = tssParticipantDirectory.getTotalShares();
        final int threshold = tssParticipantDirectory.getThreshold();
        final int version = deserializer.readInt();

        final int expectedSize = Integer.BYTES
                + Byte.BYTES
                + Integer.BYTES
                + fieldElementSize * groupElementSize
                + totalShares * fieldElementSize * groupElementSize
                + threshold * groupElementSize
                + groupElementSize * 3
                + fieldElementSize * 2;

        if (message.length != expectedSize) {
            throw new IllegalStateException("Invalid message length");
        }

        if (version != TssMessage.MESSAGE_CURRENT_VERSION) {
            throw new IllegalStateException("Invalid message version: " + version);
        }
        if (deserializer.readByte() != expectedSchema.toByte()) {
            throw new IllegalStateException("Invalid signature schema");
        }

        final int generatingShareElement = deserializer.readInt();
        final List<GroupElement> sharedRandomness =
                deserializer.readListSameSize(groupElementFunction, fieldElementSize, groupElementSize);

        final CipherText[] cipherTable = new CipherText[totalShares];
        for (int i = 0; i < totalShares; i++) {
            final List<GroupElement> values =
                    deserializer.readListSameSize(groupElementFunction, fieldElementSize, groupElementSize);
            cipherTable[i] = new CipherText(values);
        }
        final List<GroupElement> polynomialCommitment =
                deserializer.readListSameSize(groupElementFunction, threshold, groupElementSize);
        final GroupElement f = deserializer.read(groupElementFunction, groupElementSize);
        final GroupElement a = deserializer.read(groupElementFunction, groupElementSize);
        final GroupElement y = deserializer.read(groupElementFunction, groupElementSize);
        final FieldElement zR = deserializer.read(fieldElementFunction, fieldElementSize);
        final FieldElement zA = deserializer.read(fieldElementFunction, fieldElementSize);

        final CiphertextTable combinedCipherText = new CiphertextTable(sharedRandomness, cipherTable);
        final NizkProof nizkProof = new NizkProof(f, a, y, zR, zA);
        return new Groth21Message(
                version,
                expectedSchema,
                generatingShareElement,
                combinedCipherText,
                new EcPolynomial(polynomialCommitment),
                nizkProof);
    }
}
