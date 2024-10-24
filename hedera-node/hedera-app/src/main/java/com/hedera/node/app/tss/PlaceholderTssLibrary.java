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

package com.hedera.node.app.tss;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;

import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.api.TssShareSignature;
import com.hedera.node.app.tss.pairings.FakeFieldElement;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPrivateKey;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.PairingSignature;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class PlaceholderTssLibrary implements TssLibrary {
    public static final int DEFAULT_THRESHOLD = 10;
    public static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    private static final PairingPrivateKey AGGREGATED_PRIVATE_KEY =
            new PairingPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    private static final PairingPublicKey LEDGER_ID = AGGREGATED_PRIVATE_KEY.createPublicKey();

    private static final SecureRandom RANDOM = new SecureRandom();
    private final int threshold;
    private byte[] message = new byte[0];

    public PlaceholderTssLibrary(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Invalid threshold: " + threshold);
        }

        this.threshold = threshold;
    }

    public PlaceholderTssLibrary() {
        this(DEFAULT_THRESHOLD);
    }

    @NotNull
    @Override
    public TssMessage generateTssMessage(@NotNull TssParticipantDirectory tssParticipantDirectory) {
        return null;
    }

    @NotNull
    @Override
    public TssMessage generateTssMessage(
            @NotNull TssParticipantDirectory tssParticipantDirectory, @NotNull TssPrivateShare privateShare) {
        return new TssMessage(new byte[0]);
    }

    @Override
    public boolean verifyTssMessage(
            @NotNull TssParticipantDirectory participantDirectory, @NotNull TssMessage tssMessage) {
        return false;
    }

    @NotNull
    @Override
    public List<TssPrivateShare> decryptPrivateShares(
            @NotNull TssParticipantDirectory participantDirectory, @NotNull List<TssMessage> validTssMessages) {
        return List.of();
    }

    @NotNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NotNull List<TssPrivateShare> privateShares) {
        if (privateShares.size() < threshold) {
            throw new IllegalStateException("Not enough shares to aggregate");
        }
        return AGGREGATED_PRIVATE_KEY;
    }

    @NotNull
    @Override
    public List<TssPublicShare> computePublicShares(
            @NotNull TssParticipantDirectory participantDirectory, @NotNull List<TssMessage> validTssMessages) {
        return List.of();
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

        final var messageHash = noThrowSha384HashOf(message);
        final var messageGroupElement = new BigInteger(1, messageHash);
        final var privateKeyFieldElement = AGGREGATED_PRIVATE_KEY.privateKey().toBigInteger();
        final var signatureGroupElement = new FakeGroupElement(messageGroupElement.add(privateKeyFieldElement));
        return new PairingSignature(signatureGroupElement, SIGNATURE_SCHEMA);
    }

    @NotNull
    @Override
    public TssShareSignature sign(@NotNull TssPrivateShare privateShare, @NotNull byte[] message) {
        final var messageHash = noThrowSha384HashOf(message);
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
        final var fakePairingSignature = signature.signature();
        return fakePairingSignature.verify(ledgerId, message);
    }

    // This method is not part of the TssLibrary interface, used for testing purposes
    public void setTestMessage(byte[] message) {
        this.message = message;
    }
}
