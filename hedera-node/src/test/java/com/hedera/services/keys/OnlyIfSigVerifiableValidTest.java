/*
 * Copyright (C) 2021 Hedera Hashgraph, LLC
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
package com.hedera.services.keys;

import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnlyIfSigVerifiableValidTest {
    @Mock private SyncVerifier syncVerifier;

    private OnlyIfSigVerifiableValid subject;

    @BeforeEach
    void setUp() {
        subject = new OnlyIfSigVerifiableValid(syncVerifier);
    }

    @Test
    void acceptsValidSig() {
        // given:
        final var sig = new SignatureWithStatus(VerificationStatus.VALID);

        // expect:
        Assertions.assertTrue(subject.test(null, sig));
    }

    @Test
    void acceptsVerifiableValidSig() {
        // given:
        final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
        // and:
        willAnswer(
                        invocationOnMock -> {
                            final List<SignatureWithStatus> sigs = invocationOnMock.getArgument(0);
                            sigs.get(0).setStatus(VerificationStatus.VALID);
                            return null;
                        })
                .given(syncVerifier)
                .verifySync(List.of(sig));

        // expect:
        Assertions.assertTrue(subject.test(null, sig));
    }

    @Test
    void rejectsVerifiableInvalidSig() {
        // given:
        final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
        // and:
        willAnswer(
                        invocationOnMock -> {
                            final List<SignatureWithStatus> sigs = invocationOnMock.getArgument(0);
                            sigs.get(0).setStatus(VerificationStatus.INVALID);
                            return null;
                        })
                .given(syncVerifier)
                .verifySync(List.of(sig));

        // expect:
        Assertions.assertFalse(subject.test(null, sig));
    }

    @Test
    void rejectsUnverifiableSig() {
        // given:
        final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
        // and:
        willThrow(CryptographyException.class).given(syncVerifier).verifySync(List.of(sig));

        // expect:
        Assertions.assertFalse(subject.test(null, sig));
    }

    @Test
    void rejectsInvalidSig() {
        // given:
        final var sig = new SignatureWithStatus(VerificationStatus.INVALID);

        // expect:
        Assertions.assertFalse(subject.test(null, sig));
    }

    private static class SignatureWithStatus extends TransactionSignature {
        private VerificationStatus status;

        private static byte[] MEANINGLESS_BYTE = new byte[] {(byte) 0xAB};

        public SignatureWithStatus(VerificationStatus status) {
            super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
            this.status = status;
        }

        @Override
        public VerificationStatus getSignatureStatus() {
            return status;
        }

        public void setStatus(VerificationStatus status) {
            this.status = status;
        }
    }
}
