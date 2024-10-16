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

package com.swirlds.platform.state.signer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import org.junit.jupiter.api.Test;

public class StateSignerTests {

    @Test
    void doNotSignPcesState() {
        final Randotron randotron = Randotron.create();

        final Hash stateHash = randotron.nextHash();
        final SignedState signedState = new RandomSignedStateGenerator()
                .setStateHash(stateHash)
                .setPcesRound(true)
                .build();

        final PlatformSigner platformSigner = mock(PlatformSigner.class);
        final StateSigner stateSigner = new DefaultStateSigner(platformSigner);

        final ReservedSignedState reservedSignedState = signedState.reserve("test");
        final StateSignatureTransaction signatureTransaction = stateSigner.signState(reservedSignedState);
        assertTrue(reservedSignedState.isClosed());
        assertNull(signatureTransaction);
    }

    @Test
    void signRegularState() {
        final Randotron randotron = Randotron.create();

        final Hash stateHash = randotron.nextHash();
        final SignedState signedState = new RandomSignedStateGenerator()
                .setStateHash(stateHash)
                .setPcesRound(false)
                .build();

        final PlatformSigner platformSigner = mock(PlatformSigner.class);
        final Signature signature = randotron.nextSignature();
        when(platformSigner.signImmutable(any())).thenReturn(signature.getBytes());

        final StateSigner stateSigner = new DefaultStateSigner(platformSigner);

        final ReservedSignedState reservedSignedState = signedState.reserve("test");
        final StateSignatureTransaction payload = stateSigner.signState(reservedSignedState);
        assertTrue(reservedSignedState.isClosed());
        assertNotNull(payload);
        assertEquals(payload.signature(), signature.getBytes());
    }
}
