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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class HapiSignatureVerifierImplTest extends AppTestBase implements Scenarios {
    /**
     * The "signed" bytes to test with. This really doesn't matter because we mock out the crypto engine, so it will
     * always return true (or false) as needed regardless of the actual bytes.
     */
    private Bytes signedBytes;

    private Cryptography cryptoEngine;

    @BeforeEach
    void setUp() {
        signedBytes = randomBytes(123);
        cryptoEngine = mock(Cryptography.class);
    }

    /**
     * If we are asked to verify the keys for an empty signature map, then we "default to closed", or "default to fail".
     * If there are no signatures on the map, it must not mean that the transaction is authorized, it must mean that the
     * transaction is not authorized. So if we try to do a sign check for a key and find that there are no signatures
     * to verify, then the response must be false.
     *
     * @param key The key to test
     */
    @ParameterizedTest
    @MethodSource(value = "provideMixOfAllKindsOfKeys")
    void failToVerifyIfSignaturesAreEmpty(@NonNull final Key key) {
        final var verifier = new HapiSignatureVerifierImpl(cryptoEngine);
        final var result = verifier.verify(signedBytes, List.of(), key);
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(false);
    }

    /**
     * If the signed bytes don't match the signatures, then the verification should fail.
     *
     * @param key the key to test
     */
    @ParameterizedTest
    @MethodSource(value = "provideMixOfAllKindsOfKeys")
    void failIfCryptoEngineSaysTheSignatureWasBad(@NonNull final Key key) {
        when(cryptoEngine.verifyAsync(any(), any(), any(), any())).thenReturn(completedFuture(false));
        final var sigPairs = sufficientSignatures(key);
        final var verifier = new HapiSignatureVerifierImpl(cryptoEngine);
        final var result = verifier.verify(signedBytes, sigPairs, key);
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(false);
    }

    /**
     * If we simply do not have enough signatures to satisfy the key, then the verification should fail.
     * @param key the key to test
     */
    @ParameterizedTest
    @MethodSource(value = "provideMixOfAllKindsOfKeys")
    void failToVerifyIfSignaturesAreInsufficient(@NonNull final Key key) {
        lenient().when(cryptoEngine.verifyAsync(any(), any(), any(), any())).thenReturn(completedFuture(true));
        final var verifier = new HapiSignatureVerifierImpl(cryptoEngine);
        final var sigPairs = insufficientSignatures(key);
        final var result = verifier.verify(signedBytes, sigPairs, key);
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(false);
    }

    /**
     * If we have enough signatures to satisfy the key, then the verification should succeed.
     * @param key the key to test
     */
    @ParameterizedTest
    @MethodSource(value = "provideMixOfAllKindsOfKeys")
    void verifyIfSufficientSignatures(@NonNull final Key key) {
        lenient().when(cryptoEngine.verifyAsync(any(), any(), any(), any())).thenReturn(completedFuture(true));
        final var verifier = new HapiSignatureVerifierImpl(cryptoEngine);
        final var sigPairs = sufficientSignatures(key);
        final var result = verifier.verify(signedBytes, sigPairs, key);
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(true);
    }

    /**
     * If the public key prefix is missing, then the signature will not be selected, and will therefore not be
     * part of verification.
     */
    @Test
    void verifyFailIfPrefixIsMissing() {
        final var verifier = new HapiSignatureVerifierImpl(cryptoEngine);
        final var key = FAKE_ED25519_KEY_INFOS[0].publicKey();
        final var sigPairs = sufficientSignatures(key).stream()
                .map(sigPair -> sigPair.copyBuilder().pubKeyPrefix(Bytes.EMPTY).build())
                .collect(Collectors.toList());
        final var result = verifier.verify(signedBytes, sigPairs, key);
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(false);
    }

    private static Key keyList(Key... keys) {
        return Key.newBuilder().keyList(KeyList.newBuilder().keys(keys)).build();
    }

    private static Key thresholdKey(int threshold, Key... keys) {
        return Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .keys(KeyList.newBuilder().keys(keys))
                        .threshold(threshold))
                .build();
    }

    static Stream<Arguments> provideMixOfAllKindsOfKeys() {
        // FUTURE: Add RSA keys to this list
        return Stream.of(
                // Single element keys
                Arguments.of(FAKE_ED25519_KEY_INFOS[0].publicKey()),
                Arguments.of(FAKE_ECDSA_KEY_INFOS[0].publicKey()),

                // Single element key lists of different key types
                Arguments.of(keyList(FAKE_ED25519_KEY_INFOS[0].publicKey())),
                Arguments.of(keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                // Multiple element key lists of mixed types
                Arguments.of(keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                // Nested key lists
                Arguments.of(keyList(
                        FAKE_ED25519_KEY_INFOS[0].publicKey(),
                        keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()),
                        FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                // Key lists with threshold keys
                Arguments.of(keyList(
                        FAKE_ED25519_KEY_INFOS[0].publicKey(),
                        thresholdKey(1, FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()),
                        FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                // Single element threshold keys of different key types
                Arguments.of(thresholdKey(1, FAKE_ED25519_KEY_INFOS[0].publicKey())),
                Arguments.of(thresholdKey(1, FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                // Multiple element threshold keys of mixed types
                Arguments.of(
                        thresholdKey(1, FAKE_ED25519_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey())),
                Arguments.of(
                        thresholdKey(1, FAKE_ED25519_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey())),

                // Nested Threshold keys
                Arguments.of(thresholdKey(
                        3,
                        keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[0].publicKey()),
                        FAKE_ED25519_KEY_INFOS[2].publicKey(),
                        FAKE_ECDSA_KEY_INFOS[2].publicKey(),
                        thresholdKey(1, FAKE_ECDSA_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()))));
    }

    /** Based on the kind of key, returns a list of {@link SignaturePair}s that will pass verification. */
    private static List<SignaturePair> sufficientSignatures(@NonNull final Key key) {
        return switch (key.key().kind()) {
            case KEY_LIST -> sufficientSignatures(key.keyListOrThrow());
            case THRESHOLD_KEY -> sufficientSignatures(key.thresholdKeyOrThrow());
            case ED25519 -> List.of(passingSigPairED25519(key));
            case ECDSA_SECP256K1 -> List.of(passingSigPairECDSA(key));
            default -> throw new IllegalArgumentException(
                    "Unsupported key type: " + key.key().kind());
        };
    }

    private static List<SignaturePair> sufficientSignatures(@NonNull final KeyList key) {
        return key.keysOrThrow().stream()
                .map(HapiSignatureVerifierImplTest::sufficientSignatures)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private static List<SignaturePair> sufficientSignatures(@NonNull final ThresholdKey key) {
        final var sigPairs = sufficientSignatures(key.keysOrThrow());
        final var numToRemove = sigPairs.size() - key.threshold();
        if (numToRemove > 0) {
            sigPairs.subList(0, numToRemove).clear();
        }
        return sigPairs;
    }

    private static SignaturePair passingSigPairECDSA(@NonNull final Key primitive) {
        final Bytes bytes = primitive.key().as();
        return SignaturePair.newBuilder()
                .pubKeyPrefix(bytes.getBytes(0, 10))
                .ecdsaSecp256k1(bytes)
                .build();
    }

    private static SignaturePair passingSigPairED25519(@NonNull final Key primitive) {
        final Bytes bytes = primitive.key().as();
        return SignaturePair.newBuilder()
                .pubKeyPrefix(bytes.getBytes(0, 10))
                .ed25519(bytes)
                .build();
    }

    private static List<SignaturePair> insufficientSignatures(@NonNull final Key key) {
        final var sigPairs = new ArrayList<>(sufficientSignatures(key));
        sigPairs.remove(0);
        return sigPairs;
    }
}
