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

package com.hedera.node.app.signature.impl;

import static java.util.Collections.emptySet;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
final class SignatureVerifierImplTest extends AppTestBase implements Scenarios {
    /**
     * The "signed" bytes to test with. This really doesn't matter because we mock out the crypto engine, so it will
     * always return true (or false) as needed regardless of the actual bytes.
     */
    private Bytes signedBytes;
    /** The Crypto engine. We need this to be mocked, so we can change the behavior of the crypto engine. */
    @Mock
    private Cryptography cryptoEngine;
    /** Captures the args sent to the crypto engine. */
    @Captor
    ArgumentCaptor<List<TransactionSignature>> sigsCaptor;
    /** The verifier under test. */
    private SignatureVerifierImpl verifier;

    @BeforeEach
    void setUp() {
        signedBytes = randomBytes(123);
        verifier = new SignatureVerifierImpl(cryptoEngine);
    }

    @Test
    @DisplayName("Null Args are not permitted")
    void failIfConstructorArgsAreNull() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new SignatureVerifierImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null Args are not permitted")
    void failIfArgsAreNull() {
        final Set<ExpandedSignaturePair> signatures = emptySet();
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> verifier.verify(null, signatures)).isInstanceOf(NullPointerException.class);
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> verifier.verify(signedBytes, null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * If we are asked to verify the keys for an empty signature map, then we get back an empty map, since there
     * was nothing to verify.
     */
    @Test
    @DisplayName("If there are no signatures then the map is empty")
    void noSignatures() {
        final var result = verifier.verify(signedBytes, emptySet());
        assertThat(result).isEmpty();
    }

    /**
     * Each {@link ExpandedSignaturePair} sent to the {@link SignatureVerifier} will be included in the
     * returned map.
     */
    @Test
    @DisplayName("All signatures are included in the result")
    void someSignatures() {
        // Given some different kinds of signatures, and a crypto engine that successfully finishes
        // every single signature check right away
        final var sigs = Set.of(
                ecdsaPair(ALICE.keyInfo().publicKey()),
                ed25519Pair(BOB.keyInfo().publicKey()),
                hollowPair(ERIN.keyInfo().publicKey(), ERIN.account()));

        //noinspection unchecked
        doAnswer((Answer<Void>) invocation -> {
                    final List<TransactionSignature> signatures = invocation.getArgument(0);
                    for (TransactionSignature signature : signatures) {
                        signature.setSignatureStatus(VerificationStatus.VALID);
                        signature.setFuture(completedFuture(null));
                    }
                    return null;
                })
                .when(cryptoEngine)
                .verifyAsync(any(List.class));

        // When we verify them
        final var map = verifier.verify(signedBytes, sigs);

        // Then we find each of the keys are present
        assertThat(map).hasSize(3);

        var future = map.get(ALICE.keyInfo().publicKey());
        assertThat(future).isNotNull();
        assertThat(future)
                .succeedsWithin(1, TimeUnit.SECONDS)
                .extracting("passed")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("Crypto Engine is given array with all the required data")
    void cryptoEngineIsGivenAllTheData() {
        // Given some different kinds of signatures
        final var sigs = new LinkedHashSet<ExpandedSignaturePair>(); // for predictable iteration for the test
        sigs.add(ecdsaPair(ALICE.keyInfo().publicKey()));
        sigs.add(ed25519Pair(BOB.keyInfo().publicKey()));
        sigs.add(hollowPair(ERIN.keyInfo().publicKey(), ERIN.account()));
        doNothing().when(cryptoEngine).verifyAsync(sigsCaptor.capture());

        // The signed bytes for ECDSA keys are keccak hashes
        final var signedBytesArray = new byte[(int) signedBytes.length()];
        signedBytes.getBytes(0, signedBytesArray, 0, signedBytesArray.length);
        final var keccakSignedBytes = Bytes.wrap(MiscCryptoUtils.keccak256DigestOf(signedBytesArray));

        // When we verify them
        verifier.verify(signedBytes, sigs);

        // Then we find the crypto engine was given an array with all the data
        final var txSigs = sigsCaptor.getValue();
        assertThat(txSigs).hasSize(3);

        final var itr = sigs.iterator();
        for (int i = 0; i < 3; i++) {
            final var expandedSigPair = itr.next();
            final var txSig = txSigs.get(i);
            final var contents = Bytes.wrap(txSig.getContents());
            assertThat(contents.slice(txSig.getMessageOffset(), txSig.getMessageLength())
                            .matchesPrefix(i == 1 ? signedBytes : keccakSignedBytes)) // index 1 is ed25519
                    .isTrue();

            assertThat(contents.slice(txSig.getSignatureOffset(), txSig.getSignatureLength())
                            .matchesPrefix(expandedSigPair.signature()))
                    .isTrue();

            assertThat(contents.slice(txSig.getPublicKeyOffset(), txSig.getPublicKeyLength())
                            .matchesPrefix(expandedSigPair.keyBytes()))
                    .isTrue();
        }
    }

    /** Simple utility to create an ECDSA_SECP256K1 expanded signature */
    private ExpandedSignaturePair ecdsaPair(final Key key) {
        final var compressed = key.ecdsaSecp256k1OrThrow();
        final var array = new byte[(int) compressed.length()];
        compressed.getBytes(0, array);
        final var decompressed = MiscCryptoUtils.decompressSecp256k1(array);
        final var sigPair = SignaturePair.newBuilder()
                .pubKeyPrefix(key.ecdsaSecp256k1OrThrow())
                .ecdsaSecp256k1(key.ecdsaSecp256k1OrThrow())
                .build();
        return new ExpandedSignaturePair(key, Bytes.wrap(decompressed), null, sigPair);
    }

    /** Simple utility to create an ED25519 expanded signature */
    private ExpandedSignaturePair ed25519Pair(final Key key) {
        final var sigPair = SignaturePair.newBuilder()
                .pubKeyPrefix(key.ed25519OrThrow())
                .ed25519(key.ed25519OrThrow())
                .build();
        return new ExpandedSignaturePair(key, key.ed25519OrThrow(), null, sigPair);
    }

    /** Simple utility to create an ECDSA_SECP256K1 hollow account based expanded signature */
    private ExpandedSignaturePair hollowPair(final Key key, @NonNull final Account hollowAccount) {
        final var compressed = key.ecdsaSecp256k1OrThrow();
        final var array = new byte[(int) compressed.length()];
        compressed.getBytes(0, array);
        final var decompressed = MiscCryptoUtils.decompressSecp256k1(array);
        final var sigPair = SignaturePair.newBuilder()
                .pubKeyPrefix(key.ecdsaSecp256k1OrThrow())
                .ecdsaSecp256k1(key.ecdsaSecp256k1OrThrow())
                .build();
        return new ExpandedSignaturePair(key, Bytes.wrap(decompressed), hollowAccount.alias(), sigPair);
    }
}
