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

package com.swirlds.crypto.tss.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.swirlds.crypto.signaturescheme.api.PairingPrivateKey;
import com.swirlds.crypto.signaturescheme.api.PairingPublicKey;
import com.swirlds.crypto.signaturescheme.api.SignatureSchema;
import org.junit.jupiter.api.Test;

class TssParticipantDirectoryTest {

    @Test
    void testInvalidThreshold() {
        TssParticipantDirectory.Builder builder = TssParticipantDirectory.createBuilder();
        PairingPrivateKey privateKey = mock(PairingPrivateKey.class);
        PairingPublicKey publicKey = mock(PairingPublicKey.class);

        builder.withSelf(1, privateKey);
        builder.withParticipant(2, 1, publicKey);

        // Test threshold too low
        builder.withThreshold(0);
        Exception exception =
                assertThrows(IllegalStateException.class, () -> builder.build(mock(SignatureSchema.class)));
        assertTrue(exception.getMessage().contains("Invalid threshold"));

        builder.withThreshold(-1);
        exception = assertThrows(IllegalStateException.class, () -> builder.build(mock(SignatureSchema.class)));
        assertTrue(exception.getMessage().contains("Invalid threshold"));

        // Test threshold too high
        builder.withThreshold(3);
        exception = assertThrows(IllegalStateException.class, () -> builder.build(mock(SignatureSchema.class)));
        assertTrue(exception.getMessage().contains("Threshold exceeds the number of shares"));

        exception = assertThrows(IllegalArgumentException.class, () -> builder.withParticipant(2, 1, publicKey));
        assertTrue(exception.getMessage().contains("Participant with id 2 was previously added to the directory"));
    }

    @Test
    void testEmptyBuilder() {
        TssParticipantDirectory.Builder builder = TssParticipantDirectory.createBuilder();
        Exception exception =
                assertThrows(NullPointerException.class, () -> builder.build(mock(SignatureSchema.class)));
        assertTrue(exception.getMessage().contains("There should be an entry for the current participant"));
    }

    @Test
    void testEmptyParticipants() {
        PairingPrivateKey privateKey = mock(PairingPrivateKey.class);
        Exception exception = assertThrows(IllegalStateException.class, () -> TssParticipantDirectory.createBuilder()
                .withSelf(1, privateKey)
                .withThreshold(1)
                .build(mock(SignatureSchema.class)));

        assertTrue(exception.getMessage().contains("There should be at least one participant in the protocol"));
    }

    @Test
    void testParticipantsDoesNotContainSelf() {
        PairingPrivateKey privateKey = mock(PairingPrivateKey.class);
        PairingPublicKey publicKey = mock(PairingPublicKey.class);
        Exception exception = assertThrows(IllegalStateException.class, () -> TssParticipantDirectory.createBuilder()
                .withSelf(1, privateKey)
                .withParticipant(2, 1, publicKey)
                .withThreshold(1)
                .build(mock(SignatureSchema.class)));

        assertTrue(exception
                .getMessage()
                .contains("The participant list does not contain a reference to the current participant"));
    }

    @Test
    void testValidConstruction() {
        PairingPrivateKey privateKey = mock(PairingPrivateKey.class);
        PairingPublicKey publicKey = mock(PairingPublicKey.class);
        TssParticipantDirectory directory = TssParticipantDirectory.createBuilder()
                .withSelf(1, privateKey)
                .withParticipant(1, 1, publicKey)
                .withThreshold(1)
                .build(mock(SignatureSchema.class));

        assertNotNull(directory);
    }
}
