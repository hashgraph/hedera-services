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

package com.hedera.services.bdd.junit.hedera.embedded.fakes.tss;

import com.hedera.cryptography.pairings.signatures.api.PairingPrivateKey;
import com.hedera.cryptography.pairings.signatures.api.PairingPublicKey;
import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import com.hedera.cryptography.pairings.signatures.api.SignatureSchema;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.tss.TssLibrary;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class FakeTssLibrary implements TssLibrary {
    public static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    private static final FakePairingPrivateKey AGGREGATED_PRIVATE_KEY = new FakePairingPrivateKey(
            new PairingPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA));
    private static final PairingPublicKey LEDGER_ID = AGGREGATED_PRIVATE_KEY.createPublicKey();
    private static final MessageDigest SHA_256;

    static {
        try {
            SHA_256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final int threshold;
    private byte[] message = new byte[0];

    public FakeTssLibrary(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Invalid threshold: " + threshold);
        }

        this.threshold = threshold;
    }

    @NotNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NotNull List<TssPrivateShare> privateShares) {
        if (privateShares.size() < threshold) {
            throw new IllegalStateException("Not enough shares to aggregate");
        }
        return AGGREGATED_PRIVATE_KEY.getPairingPrivateKey();
    }

    @NotNull
    @Override
    public PairingPublicKey aggregatePublicShares(@NotNull List<TssPublicShare> publicShares) {
        if (publicShares.size() < threshold) {
            return new PairingPublicKey(new FakeGroupElement(BigInteger.valueOf(RANDOM.nextLong())), SIGNATURE_SCHEMA);
        }
        return LEDGER_ID;
    }

    @NotNull
    @Override
    public PairingSignature aggregateSignatures(@NotNull List<TssShareSignature> partialSignatures) {
        if (partialSignatures.size() < threshold) {
            return new PairingSignature(new FakeGroupElement(BigInteger.valueOf(RANDOM.nextLong())), SIGNATURE_SCHEMA);
        }

        final var messageHash = computeHash(message);
        final var messageGroupElement = new BigInteger(1, messageHash);
        final var privateKeyFieldElement =
                AGGREGATED_PRIVATE_KEY.getPairingPrivateKey().privateKey().toBigInteger();
        final var signatureGroupElement = new FakeGroupElement(messageGroupElement.add(privateKeyFieldElement));
        return new PairingSignature(signatureGroupElement, SIGNATURE_SCHEMA);
    }

    @NotNull
    @Override
    public TssShareSignature sign(@NotNull TssPrivateShare privateShare, @NotNull byte[] message) {
        final var messageHash = computeHash(message);
        final var messageGroupElement = new BigInteger(1, messageHash);
        final var privateKeyFieldElement =
                privateShare.privateKey().privateKey().toBigInteger();
        final var signatureGroupElement = new FakeGroupElement(messageGroupElement.add(privateKeyFieldElement));
        final var signature = new PairingSignature(signatureGroupElement, SIGNATURE_SCHEMA);
        return new TssShareSignature(privateShare.shareId(), signature);
    }

    @Override
    public boolean verifySignature(
            @NotNull TssParticipantDirectory participantDirectory,
            @NotNull List<TssPublicShare> publicShares,
            @NotNull TssShareSignature signature) {
        if (participantDirectory.getThreshold() != this.threshold) {
            throw new IllegalArgumentException("Invalid threshold");
        }

        if (publicShares.size() < threshold) {
            return false;
        }

        final PairingPublicKey ledgerId = aggregatePublicShares(publicShares);
        final var fakePairingSignature = new FakePairingSignature(signature.signature());
        return fakePairingSignature.verify(ledgerId, message);
    }

    public static byte[] computeHash(byte[] message) {
        return SHA_256.digest(message);
    }

    // This method is not part of the TssLibrary interface, used for testing purposes
    void setTestMessage(byte[] message) {
        this.message = message;
    }
}
