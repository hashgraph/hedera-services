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

package com.hedera.node.app.signature;

import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.failedVerification;
import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.passedVerification;
import static com.hedera.node.app.spi.fixtures.Scenarios.ALICE;
import static com.hedera.node.app.spi.fixtures.Scenarios.ERIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelegateKeyVerifierTest {

    private static final Key KEY = ALICE.keyInfo().publicKey();
    private static final Bytes ALIAS = ERIN.account().alias();

    @Mock
    private Predicate<Key> parentCallback;

    @Mock
    private VerificationAssistant childCallback;

    private DelegateKeyVerifier subject;

    private static Stream<Arguments> getVerificationResults() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false));
    }

    @BeforeEach
    void setUp() {
        subject = new DelegateKeyVerifier(parentCallback);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        assertThatThrownBy(() -> new DelegateKeyVerifier(null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> subject.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor(null, childCallback)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor(KEY, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSimpleVerificationFor(boolean callbackResult) {
        // given
        when(parentCallback.test(KEY)).thenReturn(callbackResult);

        // when
        final var actual = subject.verificationFor(KEY);

        // then
        final var expected = callbackResult ? passedVerification(KEY) : failedVerification(KEY);
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("getVerificationResults")
    void testVerificationForFailingCallbacks(boolean parentResult, boolean childResult) {
        // given
        when(parentCallback.test(KEY)).thenReturn(parentResult);

        final var intermediateVerification = parentResult ? passedVerification(KEY) : failedVerification(KEY);
        when(childCallback.test(KEY, intermediateVerification)).thenReturn(childResult);

        // when
        final var actual = subject.verificationFor(KEY, childCallback);

        // then
        final var expected = childResult ? passedVerification(KEY) : failedVerification(KEY);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testSimpleVerificationForAccount() {
        // when
        final var result = subject.verificationFor(ALIAS);

        // then
        assertThat(result).isEqualTo(failedVerification(ALIAS));
    }
}
