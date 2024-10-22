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

import static com.hedera.node.app.tss.PlaceholderTssLibrary.SIGNATURE_SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.api.TssShareId;
import com.hedera.node.app.tss.api.TssShareSignature;
import com.hedera.node.app.tss.pairings.FakeFieldElement;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPrivateKey;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.PairingSignature;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceholderTssLibraryTest {

    @Test
    void sign() {
        final var fakeTssLibrary = new PlaceholderTssLibrary(1);
        final var privateKeyElement = new FakeFieldElement(BigInteger.valueOf(2L));
        final var pairingPrivateKey = new PairingPrivateKey(privateKeyElement, SIGNATURE_SCHEMA);
        final var privateShare = new TssPrivateShare(new TssShareId(1), pairingPrivateKey);

        final var tssShareSignature = fakeTssLibrary.sign(privateShare, "Hello, World!".getBytes());

        assertNotNull(tssShareSignature);
        assertEquals(privateShare.shareId(), tssShareSignature.shareId());
        assertNotNull(tssShareSignature.signature());
    }

    @Test
    void aggregatePrivateShares() {
        final var fakeTssLibrary = new PlaceholderTssLibrary(2);
        final var privateShares = new ArrayList<TssPrivateShare>();
        final var privateKeyShares = new long[] {1, 2, 3};
        for (int i = 0; i < privateKeyShares.length; i++) {
            final var privateKeyElement = new FakeFieldElement(BigInteger.valueOf(privateKeyShares[i]));
            privateShares.add(
                    new TssPrivateShare(new TssShareId(i), new PairingPrivateKey(privateKeyElement, SIGNATURE_SCHEMA)));
        }

        final var aggregatedPrivateKey = fakeTssLibrary.aggregatePrivateShares(privateShares);

        assertNotNull(aggregatedPrivateKey);
        assertEquals("42", aggregatedPrivateKey.privateKey().toBigInteger().toString());
    }

    @Test
    void aggregatePrivateSharesWithNotEnoughShares() {
        final var fakeTssLibrary = new PlaceholderTssLibrary(3);
        final var privateShares = new ArrayList<TssPrivateShare>();
        final var privateKeyShares = new long[] {1, 2};
        for (int i = 0; i < privateKeyShares.length; i++) {
            final var privateKeyElement = new FakeFieldElement(BigInteger.valueOf(privateKeyShares[i]));
            privateShares.add(
                    new TssPrivateShare(new TssShareId(i), new PairingPrivateKey(privateKeyElement, SIGNATURE_SCHEMA)));
        }

        assertTrue(assertThrows(IllegalStateException.class, () -> fakeTssLibrary.aggregatePrivateShares(privateShares))
                .getMessage()
                .contains("Not enough shares to aggregate"));
    }

    @Test
    void aggregatePublicShares() {
        final var fakeTssLibrary = new PlaceholderTssLibrary(2);
        final var publicShares = new ArrayList<TssPublicShare>();
        final var publicKeyShares = new long[] {1, 2, 3};
        for (int i = 0; i < publicKeyShares.length; i++) {
            final var publicKeyElement = new FakeGroupElement(BigInteger.valueOf(publicKeyShares[i]));
            publicShares.add(
                    new TssPublicShare(new TssShareId(i), new PairingPublicKey(publicKeyElement, SIGNATURE_SCHEMA)));
        }

        final var aggregatedPublicKey = fakeTssLibrary.aggregatePublicShares(publicShares);

        assertNotNull(aggregatedPublicKey);
        assertEquals("47", new BigInteger(1, aggregatedPublicKey.publicKey().toBytes()).toString());
    }

    @Test
    void aggregateSignatures() {
        final var fakeTssLibrary = new PlaceholderTssLibrary(2);
        final var partialSignatures = new ArrayList<TssShareSignature>();
        final var signatureShares = new long[] {1, 2, 3};
        for (int i = 0; i < signatureShares.length; i++) {
            final var signatureElement = new FakeGroupElement(BigInteger.valueOf(signatureShares[i]));
            partialSignatures.add(
                    new TssShareSignature(new TssShareId(i), new PairingSignature(signatureElement, SIGNATURE_SCHEMA)));
        }

        final var aggregatedSignature = fakeTssLibrary.aggregateSignatures(partialSignatures);

        assertNotNull(aggregatedSignature);
        final var expectedSignature =
                "8725231785142640510958974801449281668044511174527971820957835005137448197712608590715499503138764434364488379578757";
        assertEquals(
                expectedSignature,
                new BigInteger(1, aggregatedSignature.signature().toBytes()).toString());
    }

    @Test
    void verifySignature() {
        final var privateKeyElement = new FakeFieldElement(BigInteger.valueOf(42L));
        final var pairingPrivateKey = new PairingPrivateKey(privateKeyElement, SIGNATURE_SCHEMA);
        final var pairingPublicKey = pairingPrivateKey.createPublicKey();
        final var p0PrivateShare = new TssPrivateShare(new TssShareId(0), pairingPrivateKey);

        final var tssDirectoryBuilder = TssParticipantDirectory.createBuilder()
                .withSelf(0, pairingPrivateKey)
                .withParticipant(0, 1, pairingPublicKey);

        final var publicShares = new ArrayList<TssPublicShare>();
        publicShares.add(new TssPublicShare(new TssShareId(0), pairingPublicKey));

        final var publicKeyShares = new long[] {37L, 73L};
        for (int i = 0; i < publicKeyShares.length; i++) {
            final var publicKeyElement = new FakeGroupElement(BigInteger.valueOf(publicKeyShares[i]));
            final var publicKey = new PairingPublicKey(publicKeyElement, SIGNATURE_SCHEMA);
            publicShares.add(new TssPublicShare(new TssShareId(i + 1), publicKey));
            tssDirectoryBuilder.withParticipant(i + 1, 1, publicKey);
        }

        final var threshold = 2;
        final var fakeTssLibrary = new PlaceholderTssLibrary(threshold);
        final PairingPublicKey ledgerID = fakeTssLibrary.aggregatePublicShares(publicShares);

        final TssParticipantDirectory p0sDirectory =
                tssDirectoryBuilder.withThreshold(threshold).build(SIGNATURE_SCHEMA);

        // then
        // After genesis, and assuming the same participantDirectory p0 will have a list of 1 private share

        final SecureRandom random = new SecureRandom();
        final byte[] messageToSign = new byte[20];
        random.nextBytes(messageToSign);

        final List<TssPrivateShare> privateShares = List.of(p0PrivateShare);
        final List<TssShareSignature> signatures = new ArrayList<>();
        for (TssPrivateShare privateShare : privateShares) {
            signatures.add(fakeTssLibrary.sign(privateShare, messageToSign));
        }

        // After signing, it will collect all other participant signatures
        final List<TssShareSignature> p1Signatures = List.of(new TssShareSignature(
                new TssShareId(1), signatures.getFirst().signature())); // pretend we get another valid signature
        final byte[] invalidSignature = new byte[20];
        random.nextBytes(invalidSignature);
        final List<TssShareSignature> p2Signatures = List.of(new TssShareSignature(
                new TssShareId(2),
                new PairingSignature(new FakeGroupElement(new BigInteger(1, invalidSignature)), SIGNATURE_SCHEMA)));

        final List<TssShareSignature> collectedSignatures = new ArrayList<>();
        collectedSignatures.addAll(signatures);
        collectedSignatures.addAll(p1Signatures);
        collectedSignatures.addAll(p2Signatures);

        fakeTssLibrary.setTestMessage(messageToSign);
        final List<TssShareSignature> validSignatures = collectedSignatures.stream()
                .filter(sign -> fakeTssLibrary.verifySignature(p0sDirectory, publicShares, sign))
                .toList();

        final PairingSignature aggregatedSignature = fakeTssLibrary.aggregateSignatures(validSignatures);
        assertTrue(aggregatedSignature.verify(ledgerID, messageToSign));
    }
}
