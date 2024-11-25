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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.spi.AppContext;

import java.security.SecureRandom;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssLibraryImplTest {
    private static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[]{1});

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
    void aggregatePublicShares() {
        final var fakeTssLibrary = new TssLibraryImpl(appContext);
        final var publicShares = new ArrayList<TssPublicShare>();
        final var publicKeyShares = new long[]{1, 2, 3};
        for (int i = 0; i < publicKeyShares.length; i++) {
            publicShares.add(new TssPublicShare(
                    i,
                    BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom()).createPublicKey()));
        }

        final var aggregatedPublicKey = fakeTssLibrary.aggregatePublicShares(publicShares);

        assertNotNull(aggregatedPublicKey);
    }

    @Test
    void verifySignature() {
        final var tssLibrary = new TssLibraryImpl(appContext);
        final var participantDirectory = mock(TssParticipantDirectory.class);
        final var publicShares = new ArrayList<TssPublicShare>();
        publicShares.add(mock(TssPublicShare.class));
        final var signature = mock(TssShareSignature.class);
        given(signature.shareId()).willReturn(1);

        tssLibrary.verifySignature(participantDirectory, publicShares, signature);
        verify(signature).verify(any(), any());
    }
}
