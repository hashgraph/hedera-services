/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.verifier;

import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.failedVerification;
import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.passedVerification;
import static com.hedera.node.app.spi.fixtures.Scenarios.ALICE;
import static com.hedera.node.app.spi.fixtures.Scenarios.ERIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildHandleContextVerifierTest {

    private static final Key KEY = ALICE.keyInfo().publicKey();
    private static final Bytes ALIAS = ERIN.account().alias();

    @Mock
    private BaseHandleContextVerifier parentVerifier;

    @Mock
    private VerificationAssistant parentCallback;

    @Mock
    private VerificationAssistant childCallback;

    private ChildHandleContextVerifier subject;

    private static Stream<Arguments> getVerificationResults() {
        return Stream.of(
                Arguments.of(false, true, true),
                Arguments.of(false, true, false),
                Arguments.of(false, false, true),
                Arguments.of(false, false, false),
                Arguments.of(true, true, true),
                Arguments.of(true, true, false),
                Arguments.of(true, false, true),
                Arguments.of(true, false, false));
    }

    @BeforeEach
    void setup() {
        subject = new ChildHandleContextVerifier(parentVerifier, parentCallback);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        assertThatThrownBy(() -> new ChildHandleContextVerifier(null, parentCallback))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChildHandleContextVerifier(parentVerifier, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> subject.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor(null, childCallback)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor(KEY, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSimpleVerificationFor() {
        // when
        subject.verificationFor(KEY);

        // then
        verify(parentVerifier).verificationFor(KEY, parentCallback);
    }

    @ParameterizedTest
    @MethodSource("getVerificationResults")
    void testVerificationForFailingCallbacks(boolean originalResult, boolean parentResult, boolean childResult) {
        // given
        final var originalVerification = originalResult ? passedVerification(KEY) : failedVerification(KEY);
        when(parentCallback.test(KEY, originalVerification)).thenReturn(parentResult);

        final var intermediateVerification = parentResult ? passedVerification(KEY) : failedVerification(KEY);
        when(childCallback.test(KEY, intermediateVerification)).thenReturn(childResult);

        final var captor = ArgumentCaptor.forClass(VerificationAssistant.class);
        final var verification = mock(SignatureVerification.class);
        when(parentVerifier.verificationFor(eq(KEY), captor.capture())).thenReturn(verification);

        // when
        final var result = subject.verificationFor(KEY, childCallback);

        // then
        assertThat(result).isEqualTo(verification);
        assertThat(captor.getValue().test(KEY, originalVerification)).isEqualTo(childResult);
    }

    @ParameterizedTest
    @MethodSource("getVerificationResults")
    void testVerificationForFailingCallbacks(boolean parentResult, boolean childResult) {
        // given
        when(parentCallback.test(eq(KEY), any())).thenReturn(parentResult);
        final var intermediateResult = parentResult ? passedVerification(KEY) : failedVerification(KEY);
        when(childCallback.test(KEY, intermediateResult)).thenReturn(childResult);
        final var captor = ArgumentCaptor.forClass(VerificationAssistant.class);
        final var verification = mock(SignatureVerification.class);
        when(parentVerifier.verificationFor(eq(KEY), captor.capture())).thenReturn(verification);

        // when
        final var result = subject.verificationFor(KEY, childCallback);

        // then
        assertThat(result).isEqualTo(verification);
        assertThat(captor.getValue().test(KEY, passedVerification(KEY))).isEqualTo(childResult);
        assertThat(captor.getValue().test(KEY, failedVerification(KEY))).isEqualTo(childResult);
    }

    @Test
    void testSimpleVerificationForAccount() {
        // when
        subject.verificationFor(ALIAS);

        // then
        verify(parentVerifier).verificationFor(ALIAS);
    }
}
