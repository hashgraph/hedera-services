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

package com.swirlds.crypto.tss;

import static org.mockito.Mockito.mock;

import com.swirlds.crypto.signaturescheme.api.PairingPrivateKey;
import com.swirlds.crypto.signaturescheme.api.PairingPublicKey;
import com.swirlds.crypto.signaturescheme.api.PairingSignature;
import com.swirlds.crypto.signaturescheme.api.SignatureSchema;
import com.swirlds.crypto.tss.api.TssMessage;
import com.swirlds.crypto.tss.api.TssParticipantDirectory;
import com.swirlds.crypto.tss.api.TssPrivateShare;
import com.swirlds.crypto.tss.api.TssPublicShare;
import com.swirlds.crypto.tss.api.TssShareSignature;
import com.swirlds.crypto.tss.impl.TssServiceImpl;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A test to showcase the Tss protocol for a specific case
 * More validations can be added once
 */
class TssTest {

    @Test
    void testGenesis() {
        // Simulates the genesis process for a 3 participant network
        PairingPublicKey publicKey1 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey2 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey3 = mock(PairingPublicKey.class);

        TssParticipantDirectory p0sDirectory = TssParticipantDirectory.createBuilder()
                .withSelf(0, mock(PairingPrivateKey.class))
                .withParticipant(0, 1, publicKey1)
                .withParticipant(1, 1, publicKey2)
                .withParticipant(2, 1, publicKey3)
                .withThreshold(2)
                .build(mock(SignatureSchema.class));

        TssServiceImpl tssService = new TssServiceImpl();

        // this message will contain a random share split in 3 parts
        TssMessage p0Message = tssService.generateTssMessage(p0sDirectory);

        TssParticipantDirectory p1sDirectory = TssParticipantDirectory.createBuilder()
                .withSelf(1, mock(PairingPrivateKey.class))
                .withParticipant(0, 1, publicKey1)
                .withParticipant(1, 1, publicKey2)
                .withParticipant(2, 1, publicKey3)
                .withThreshold(2)
                .build(mock(SignatureSchema.class));

        // this message will contain a random share split in 3 parts
        TssMessage p1Message = tssService.generateTssMessage(p1sDirectory);

        TssParticipantDirectory p2sDirectory = TssParticipantDirectory.createBuilder()
                .withSelf(2, mock(PairingPrivateKey.class))
                .withParticipant(0, 1, publicKey1)
                .withParticipant(1, 1, publicKey2)
                .withParticipant(2, 1, publicKey3)
                .withThreshold(2)
                .build(mock(SignatureSchema.class));

        // this message will contain a random share split in 3 parts
        TssMessage p2Message = tssService.generateTssMessage(p2sDirectory);

        // Some other piece will distribute messages across all participants

        // And simulating processing in P0
        List<TssMessage> messages = List.of(p0Message, p1Message, p2Message);
        List<TssMessage> validMessages = messages.stream()
                .filter(tssMessage -> tssService.verifyTssMessage(p0sDirectory, tssMessage))
                .toList();

        if (validMessages.size() < p0sDirectory.getThreshold()) {
            throw new IllegalStateException("There should be at least threshold number of valid messages");
        }

        // Get the list of PrivateShares owned by participant 0
        List<TssPrivateShare> privateShares = Objects.requireNonNull(
                tssService.decryptPrivateShares(p0sDirectory, validMessages),
                "Condition of threshold number of messages was not met");

        // Get the list of PublicShares
        List<TssPublicShare> publicShares = Objects.requireNonNull(
                tssService.computePublicShares(p0sDirectory, validMessages),
                "Condition of threshold number of messages was not met");

        // Get the ledgerId
        PairingPublicKey ledgerId = tssService.aggregatePublicShares(publicShares);
    }

    @Test
    void testSigning() {
        // given:
        // all this will be calculated at genesis
        PairingPublicKey publicKey1 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey2 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey3 = mock(PairingPublicKey.class);

        TssParticipantDirectory p0sDirectory = TssParticipantDirectory.createBuilder()
                .withSelf(0, mock(PairingPrivateKey.class))
                .withParticipant(0, 1, publicKey1)
                .withParticipant(1, 1, publicKey2)
                .withParticipant(2, 1, publicKey3)
                .withThreshold(2)
                .build(mock(SignatureSchema.class));

        TssServiceImpl tssService = new TssServiceImpl();
        List<TssPublicShare> publicShares =
                List.of(mock(TssPublicShare.class), mock(TssPublicShare.class), mock(TssPublicShare.class));
        PairingPublicKey ledgerID = tssService.aggregatePublicShares(publicShares);
        List<TssPrivateShare> privateShares = List.of(mock(TssPrivateShare.class));

        SecureRandom random = new SecureRandom();
        byte[] messageToSign = new byte[20];
        random.nextBytes(messageToSign);

        // then
        // After genesis, and assuming the same participantDirectory p0 will have a list of 1 private share

        List<TssShareSignature> signatures = new ArrayList<>();
        for (TssPrivateShare privateShare : privateShares) {
            signatures.add(tssService.sign(privateShare, messageToSign));
        }

        // After signing, it will collect all other participant signatures
        List<TssShareSignature> p1Signatures = List.of(mock(TssShareSignature.class));
        List<TssShareSignature> p2Signatures = List.of(mock(TssShareSignature.class));

        List<TssShareSignature> collectedSignatures = new ArrayList<>();
        collectedSignatures.addAll(signatures);
        collectedSignatures.addAll(p1Signatures);
        collectedSignatures.addAll(p2Signatures);

        List<TssShareSignature> validSignatures = collectedSignatures.stream()
                .filter(s -> tssService.verifySignature(p0sDirectory, publicShares, s))
                .toList();

        PairingSignature signature = tssService.aggregateSignatures(validSignatures);

        if (!signature.verifySignature(ledgerID, messageToSign)) {
            throw new IllegalStateException("Signature verification failed");
        }
    }

    @Test
    void rekeying() {
        // given:
        // all this will be calculated at genesis
        PairingPublicKey publicKey1 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey2 = mock(PairingPublicKey.class);
        PairingPublicKey publicKey3 = mock(PairingPublicKey.class);

        TssParticipantDirectory p0sDirectory = TssParticipantDirectory.createBuilder()
                .withSelf(0, mock(PairingPrivateKey.class))
                .withParticipant(0, 1, publicKey1)
                .withParticipant(1, 1, publicKey2)
                .withParticipant(2, 1, publicKey3)
                .withThreshold(2)
                .build(mock(SignatureSchema.class));

        TssServiceImpl tssService = new TssServiceImpl();
        List<TssPublicShare> publicShares =
                List.of(mock(TssPublicShare.class), mock(TssPublicShare.class), mock(TssPublicShare.class));
        PairingPublicKey ledgerID = tssService.aggregatePublicShares(publicShares);
        List<TssPrivateShare> oldP0PrivateShares = List.of(mock(TssPrivateShare.class));

        // then:
        List<TssMessage> p0Messages = new ArrayList<>();
        for (TssPrivateShare privateShare : oldP0PrivateShares) {
            p0Messages.add(tssService.generateTssMessages(p0sDirectory, privateShare));
        }
        // Collect other participants messages
        List<TssMessage> p1Messages = List.of(mock(TssMessage.class));
        List<TssMessage> p2Messages = List.of(mock(TssMessage.class));

        List<TssMessage> collectedValidMessages = Stream.of(p0Messages, p1Messages, p2Messages)
                .flatMap(Collection::stream)
                .filter(tssMessage -> tssService.verifyTssMessage(p0sDirectory, tssMessage))
                .toList();

        // Get the list of PrivateShares owned by participant 0
        List<TssPrivateShare> newP0privateShares = Objects.requireNonNull(
                tssService.decryptPrivateShares(p0sDirectory, collectedValidMessages),
                "Condition of threshold number of messages was not met");

        // Get the list of PublicShares
        List<TssPublicShare> newPublicShares = Objects.requireNonNull(
                tssService.computePublicShares(p0sDirectory, collectedValidMessages),
                "Condition of threshold number of messages was not met");

        // calculate the ledgerId out of the newly calculated publicShares
        PairingPublicKey ledgerId = tssService.aggregatePublicShares(newPublicShares);

        if (!ledgerId.equals(ledgerID)) {
            throw new IllegalStateException("LedgerId must remain constant throughout the rekeying process");
        }
    }
}
