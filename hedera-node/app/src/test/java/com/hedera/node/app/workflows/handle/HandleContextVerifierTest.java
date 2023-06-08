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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.spi.fixtures.Scenarios.ALICE;
import static com.hedera.node.app.spi.fixtures.Scenarios.BOB;
import static com.hedera.node.app.spi.fixtures.Scenarios.CAROL;
import static com.hedera.node.app.spi.fixtures.Scenarios.ERIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HandleContextVerifierTest {

    private static final SignatureVerificationImpl ALICE_VERIFICATION =
            new SignatureVerificationImpl(ALICE.keyInfo().publicKey(), null, true);
    private static final SignatureVerificationImpl BOB_VERIFICATION =
            new SignatureVerificationImpl(BOB.keyInfo().publicKey(), null, false);
    private static final SignatureVerificationImpl ERIN_VERIFICATION = new SignatureVerificationImpl(
            ERIN.keyInfo().publicKey(), ERIN.account().alias(), false);

    private static final Map<Key, SignatureVerification> VERIFICATIONS = Map.of(
            ALICE.keyInfo().publicKey(), ALICE_VERIFICATION,
            BOB.keyInfo().publicKey(), BOB_VERIFICATION,
            ERIN.keyInfo().publicKey(), ERIN_VERIFICATION);

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // then
        assertThatThrownBy(() -> new HandleContextVerifier(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> verifier.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> verifier.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testVerificationForExistingKey() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // when
        final var verification = verifier.verificationFor(ALICE.keyInfo().publicKey());

        // then
        assertThat(verification).isEqualTo(ALICE_VERIFICATION);
    }

    @Test
    void testVerificationForNonExistingKey() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // when
        final var verification = verifier.verificationFor(Key.DEFAULT);

        // then
        assertThat(verification).isNull();
    }

    @Test
    void testVerificationForExistingAlias() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // when
        final var verification = verifier.verificationFor(ERIN.account().alias());

        // then
        assertThat(verification).isEqualTo(ERIN_VERIFICATION);
    }

    @Test
    void testVerificationForShortenedAlias() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // when
        final var verification = verifier.verificationFor(ERIN.account().alias().getBytes(0, 19));

        // then
        assertThat(verification).isNull();
    }

    @Test
    void testVerificationForNonExistingAlias() {
        // given
        final var verifier = new HandleContextVerifier(VERIFICATIONS);

        // when
        final var verification = verifier.verificationFor(CAROL.account().alias());

        // then
        assertThat(verification).isNull();
    }
}
