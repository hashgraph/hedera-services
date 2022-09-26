/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.factories;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.sigs.sourcing.KeyType;
import org.junit.jupiter.api.Test;

class TxnScopedPlatformSigFactoryTest {
    private static final byte[] mockKey = "abcdefg".getBytes();
    private static final byte[] mockSig = "hijklmn".getBytes();

    @Test
    void choosesAppropriateSignatureForEd25519() {
        final var subject = mock(TxnScopedPlatformSigFactory.class);

        doCallRealMethod().when(subject).signAppropriately(KeyType.ED25519, mockKey, mockSig);

        subject.signAppropriately(KeyType.ED25519, mockKey, mockSig);

        verify(subject).signBodyWithEd25519(mockKey, mockSig);
    }

    @Test
    void choosesAppropriateSignatureForSecp256k1() {
        final var subject = mock(TxnScopedPlatformSigFactory.class);

        doCallRealMethod()
                .when(subject)
                .signAppropriately(KeyType.ECDSA_SECP256K1, mockKey, mockSig);

        subject.signAppropriately(KeyType.ECDSA_SECP256K1, mockKey, mockSig);

        verify(subject).signKeccak256DigestWithSecp256k1(mockKey, mockSig);
    }
}
