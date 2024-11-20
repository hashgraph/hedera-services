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

import static com.hedera.node.app.tss.handlers.TssUtils.SIGNATURE_SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.api.FakeFieldElement;
import com.hedera.node.app.tss.api.FakeGroupElement;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssLibraryImplTest {

    @Mock
    private AppContext appContext;

    @Test
    void sign() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var pairingPrivateKey = BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom());
        final var privateShare = new TssPrivateShare(1, pairingPrivateKey);

        final var tssShareSignature = fakeTssLibrary.sign(privateShare, "Hello, World!".getBytes());

        assertNotNull(tssShareSignature);
        assertEquals(privateShare.shareId(), tssShareSignature.shareId());
        assertNotNull(tssShareSignature.signature());
    }

    @Test
    void aggregatePrivateShares() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var privateShares = new ArrayList<TssPrivateShare>();
        final var privateKeyShares = new long[] {1, 2, 3};
        for (int i = 0; i < privateKeyShares.length; i++) {
            privateShares.add(new TssPrivateShare(i, BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom())));
        }

        final var aggregatedPrivateKey = fakeTssLibrary.aggregatePrivateShares(privateShares);

        assertNotNull(aggregatedPrivateKey);
        assertEquals("42", aggregatedPrivateKey.element().toBigInteger().toString());
    }

    @Test
    void aggregatePrivateSharesWithNotEnoughShares() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var privateShares = new ArrayList<TssPrivateShare>();
        final var privateKeyShares = new long[] {1, 2};
        for (int i = 0; i < privateKeyShares.length; i++) {
            privateShares.add(new TssPrivateShare(i, BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom())));
        }

        assertTrue(assertThrows(IllegalStateException.class, () -> fakeTssLibrary.aggregatePrivateShares(privateShares))
                .getMessage()
                .contains("Not enough shares to aggregate"));
    }

    @Test
    void aggregatePublicShares() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var publicShares = new ArrayList<TssPublicShare>();
        final var publicKeyShares = new long[] {1, 2, 3};
        for (int i = 0; i < publicKeyShares.length; i++) {
            publicShares.add(new TssPublicShare(
                    i,
                    BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom()).createPublicKey()));
        }

        final var aggregatedPublicKey = fakeTssLibrary.aggregatePublicShares(publicShares);

        assertNotNull(aggregatedPublicKey);
        assertEquals("47", new BigInteger(1, aggregatedPublicKey.toBytes()).toString());
    }

    @Test
    void aggregateSignatures() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var partialSignatures = new ArrayList<TssShareSignature>();
        final var signatureShares = new long[] {1, 2, 3};
        for (int i = 0; i < signatureShares.length; i++) {
            final var signatureElement = new FakeGroupElement(BigInteger.valueOf(signatureShares[i]));
            partialSignatures.add(new TssShareSignature(i, new BlsSignature(signatureElement, SIGNATURE_SCHEMA)));
        }

        final var aggregatedSignature = fakeTssLibrary.aggregateSignatures(partialSignatures);

        assertNotNull(aggregatedSignature);
        final var expectedSignature =
                "8725231785142640510958974801449281668044511174527971820957835005137448197712608590715499503138764434364488379578757";
        assertEquals(expectedSignature, new BigInteger(1, aggregatedSignature.toBytes()).toString());
    }

    @Test
    void verifySignature() {
        final var privateKeyElement = new FakeFieldElement(BigInteger.valueOf(42L));
        final var pairingPrivateKey = new BlsPrivateKey(privateKeyElement, SIGNATURE_SCHEMA);
        final var pairingPublicKey = pairingPrivateKey.createPublicKey();
        final var p0PrivateShare = new TssPrivateShare(0, pairingPrivateKey);

        final var tssDirectoryBuilder = TssParticipantDirectory.createBuilder().withParticipant(0, 1, pairingPublicKey);

        final var publicShares = new ArrayList<TssPublicShare>();
        publicShares.add(new TssPublicShare(0, pairingPublicKey));

        final var publicKeyShares = new long[] {37L, 73L};
        for (int i = 0; i < publicKeyShares.length; i++) {
            final var publicKeyElement = new FakeGroupElement(BigInteger.valueOf(publicKeyShares[i]));
            final var publicKey = new BlsPublicKey(publicKeyElement, SIGNATURE_SCHEMA);
            publicShares.add(new TssPublicShare(i + 1, publicKey));
            tssDirectoryBuilder.withParticipant(i + 1, 1, publicKey);
        }

        final var threshold = 2;
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final BlsPublicKey ledgerID = fakeTssLibrary.aggregatePublicShares(publicShares);

        final TssParticipantDirectory p0sDirectory =
                tssDirectoryBuilder.withThreshold(threshold).build();

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
        final List<TssShareSignature> p1Signatures = List.of(
                new TssShareSignature(1, signatures.getFirst().signature())); // pretend we get another valid signature
        final byte[] invalidSignature = new byte[20];
        random.nextBytes(invalidSignature);
        final List<TssShareSignature> p2Signatures = List.of(new TssShareSignature(
                2, new BlsSignature(new FakeGroupElement(new BigInteger(1, invalidSignature)), SIGNATURE_SCHEMA)));

        final List<TssShareSignature> collectedSignatures = new ArrayList<>();
        collectedSignatures.addAll(signatures);
        collectedSignatures.addAll(p1Signatures);
        collectedSignatures.addAll(p2Signatures);

        fakeTssLibrary.setTestMessage(messageToSign);
        final List<TssShareSignature> validSignatures = collectedSignatures.stream()
                .filter(sign -> fakeTssLibrary.verifySignature(p0sDirectory, publicShares, sign))
                .toList();

        final BlsSignature aggregatedSignature = fakeTssLibrary.aggregateSignatures(validSignatures);
        assertTrue(aggregatedSignature.verify(ledgerID, messageToSign));
    }
}
