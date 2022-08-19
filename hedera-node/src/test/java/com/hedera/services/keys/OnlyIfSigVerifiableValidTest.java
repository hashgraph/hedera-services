/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class OnlyIfSigVerifiableValidTest {
    @Mock private Future<Void> syncFuture;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private OnlyIfSigVerifiableValid subject = new OnlyIfSigVerifiableValid();

    @Test
    void acceptsValidSig() {
        final var sig = new SignatureWithStatuses(VerificationStatus.VALID);
        sig.setFuture(syncFuture);

        assertTrue(subject.test(null, sig));
    }

    @Test
    void dealsWithInterruptedException() throws ExecutionException, InterruptedException {
        final var sig = new SignatureWithStatuses(VerificationStatus.UNKNOWN);
        sig.setFuture(syncFuture);
        willThrow(InterruptedException.class).given(syncFuture).get();

        assertFalse(subject.test(null, sig));

        assertThat(
                logCaptor.warnLogs(),
                contains(
                        startsWith(
                                "Interrupted while validating signature, this will be fatal outside"
                                        + " reconnect")));
    }

    @Test
    void dealsWithExecutionException() throws ExecutionException, InterruptedException {
        final var sig = new SignatureWithStatuses(VerificationStatus.UNKNOWN);
        sig.setFuture(syncFuture);
        willThrow(ExecutionException.class).given(syncFuture).get();

        assertFalse(subject.test(null, sig));

        assertThat(
                logCaptor.errorLogs(),
                contains(startsWith("Erred while validating signature, this is likely fatal")));
    }

    @Test
    void resolvesValidSlowAsyncVerifyAsExpected() {
        final var sig =
                new SignatureWithStatuses(VerificationStatus.UNKNOWN, VerificationStatus.VALID);
        sig.setFuture(syncFuture);

        assertTrue(subject.test(null, sig));
    }

    @Test
    void resolvesInvalidSlowAsyncVerifyAsExpected() {
        final var sig =
                new SignatureWithStatuses(VerificationStatus.UNKNOWN, VerificationStatus.INVALID);
        sig.setFuture(syncFuture);

        assertFalse(subject.test(null, sig));
    }

    @Test
    void rejectsInvalidSig() {
        // given:
        final var sig = new SignatureWithStatuses(VerificationStatus.INVALID);
        sig.setFuture(syncFuture);

        // expect:
        assertFalse(subject.test(null, sig));
    }

    private static class SignatureWithStatuses extends TransactionSignature {
        private int nextStatus = 0;
        private Future<Void> future;
        private List<VerificationStatus> statuses;

        private static byte[] MEANINGLESS_BYTE = new byte[] {(byte) 0xAB};

        public SignatureWithStatuses(VerificationStatus... statuses) {
            super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
            this.statuses = Arrays.asList(statuses);
        }

        @Override
        public VerificationStatus getSignatureStatus() {
            return statuses.get(nextStatus++);
        }

        public void setStatus(VerificationStatus status) {
            this.statuses = List.of(status);
            nextStatus = 0;
        }

        @Override
        public synchronized Future<Void> waitForFuture() {
            return future;
        }

        @Override
        public void setFuture(Future<Void> future) {
            this.future = future;
        }
    }
}
