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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.tss.api.FakeFieldElement;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.List;

public class FakeTssLibrary implements TssLibrary {
    private static final String VALID_MESSAGE_PREFIX = "VALID";
    private static final String INVALID_MESSAGE_PREFIX = "INVALID";

    private static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    public static final BlsPrivateKey PRIVATE_KEY =
            new BlsPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    public static final BlsPublicKey FAKE_LEDGER_ID =
            new BlsPublicKey(new FakeGroupElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    public static final BlsSignature FAKE_SIGNATURE =
            new BlsSignature(new FakeGroupElement(BigInteger.valueOf(1L)), SIGNATURE_SCHEMA);

    public interface DirectoryAssertion {
        void assertExpected(@NonNull TssParticipantDirectory directory) throws AssertionError;
    }

    @Nullable
    private DirectoryAssertion decryptDirectoryAssertion;

    @Nullable
    private DirectoryAssertion rekeyGenerationDirectoryAssertion;

    @Nullable
    private List<TssPrivateShare> decryptedShares;

    /**
     * Returns a valid message with the given index.
     * @param i the index
     * @return the message
     */
    public static TssMessage validMessage(final int i) {
        return new FakeTssMessage((VALID_MESSAGE_PREFIX + i).getBytes());
    }

    /**
     * Returns an invalid message with the given index.
     * @param i the index
     * @return the message
     */
    public static TssMessage invalidMessage(final int i) {
        return new FakeTssMessage((INVALID_MESSAGE_PREFIX + i).getBytes());
    }

    /**
     * Returns the index of the share in the message.
     * @param message the message
     * @return the index
     */
    public static int getShareIndex(final TssMessage message) {
        final var s = new String(message.toBytes());
        return Integer.parseInt(s.substring(s.lastIndexOf('D') + 1));
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory tssParticipantDirectory) {
        return new FakeTssMessage(new byte[0]);
    }

    /**
     * Sets up the behavior to exhibit when receiving a call to
     * {@link #generateTssMessage(TssParticipantDirectory, TssPrivateShare)}.
     */
    public void setupRekeyGeneration(@NonNull final DirectoryAssertion rekeyGenerationDirectoryAssertion) {
        this.rekeyGenerationDirectoryAssertion = requireNonNull(rekeyGenerationDirectoryAssertion);
    }

    @Override
    public @NonNull TssMessage generateTssMessage(
            @NonNull final TssParticipantDirectory directory, @NonNull final TssPrivateShare privateShare) {
        requireNonNull(directory);
        requireNonNull(privateShare);
        if (rekeyGenerationDirectoryAssertion != null) {
            rekeyGenerationDirectoryAssertion.assertExpected(directory);
        }
        // The fake always returns a valid message
        return validMessage(privateShare.shareId());
    }

    @Override
    public boolean verifyTssMessage(
            @NonNull final TssParticipantDirectory participantDirectory, @NonNull final Bytes tssMessage) {
        return new String(tssMessage.toByteArray()).startsWith(VALID_MESSAGE_PREFIX);
    }

    /**
     * Sets up the behavior to exhibit when receiving a call to {@link #decryptPrivateShares(TssParticipantDirectory, List)}.
     * @param decryptDirectoryAssertion the assertion to make about the directory
     * @param decryptedShareIds the ids of the private shares to return
     */
    public void setupDecryption(
            @NonNull final DirectoryAssertion decryptDirectoryAssertion,
            @NonNull final List<Integer> decryptedShareIds) {
        requireNonNull(decryptedShareIds);
        this.decryptDirectoryAssertion = requireNonNull(decryptDirectoryAssertion);
        this.decryptedShares = decryptedShareIds.stream()
                .map(id -> new TssPrivateShare(id, PRIVATE_KEY))
                .toList();
    }

    @Override
    public @NonNull List<TssPrivateShare> decryptPrivateShares(
            @NonNull final TssParticipantDirectory directory, @NonNull final List<TssMessage> tssMessages) {
        requireNonNull(directory);
        requireNonNull(tssMessages);
        if (decryptDirectoryAssertion != null) {
            decryptDirectoryAssertion.assertExpected(directory);
        }
        return decryptedShares == null ? emptyList() : decryptedShares;
    }

    @NonNull
    @Override
    public List<TssPublicShare> computePublicShares(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        System.out.println("Computing public shares from " + validTssMessages.size() + " messages");
        return List.of();
    }

    @NonNull
    @Override
    public BlsPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        return FAKE_LEDGER_ID;
    }

    @NonNull
    @Override
    public TssShareSignature sign(@NonNull final TssPrivateShare privateShare, @NonNull final byte[] message) {
        return new TssShareSignature(
                privateShare.shareId(),
                new BlsSignature(new FakeGroupElement(BigInteger.valueOf(privateShare.shareId())), SIGNATURE_SCHEMA));
    }

    @Override
    public boolean verifySignature(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssPublicShare> publicShares,
            @NonNull final TssShareSignature signature) {
        return true;
    }

    @NonNull
    @Override
    public BlsSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures) {
        return FAKE_SIGNATURE;
    }

    @NonNull
    @Override
    public TssMessage getTssMessageFromBytes(Bytes tssMessage, TssParticipantDirectory participantDirectory) {
        return new FakeTssMessage(new byte[0]);
    }
}
