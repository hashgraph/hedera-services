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

package com.hedera.node.app.signature.hapi;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

import com.google.common.collect.Streams;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.fixtures.TestKeyInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    /** The verifier under test. */
    private SignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        signedBytes = randomBytes(123);
        verifier = new SignatureVerifierImpl(cryptoEngine);
    }

    /**
     * A set of tests based on verifying the behavior of the {@link SignatureVerifier} given a key that is to
     * be verified.
     */
    @Nested
    @DisplayName("Key-based Verification")
    final class KeyBasedTest {
        private static final int PREFIX_LENGTH = 10;

        @Test
        @DisplayName("Null Args are not permitted")
        void failIfArgsAreNull() {
            final List<SignaturePair> signatures = emptyList();
            final var key = Key.DEFAULT;
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> verifier.verify((Key) null, signedBytes, signatures))
                    .isInstanceOf(NullPointerException.class);
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> verifier.verify(key, null, signatures)).isInstanceOf(NullPointerException.class);
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> verifier.verify(key, signedBytes, null)).isInstanceOf(NullPointerException.class);
        }

        /**
         * If we are asked to verify the keys for an empty signature map, then we "default to closed", or "default to
         * fail". If there are no signatures on the map, it must NOT mean that the transaction is authorized, it must
         * mean that the transaction is not authorized! So if we try to do a signature check for a key and find that
         * there are no signatures to verify, then the response must be false.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("Verification fails if we have a good key but no signatures")
        void failToVerifyIfSignaturesAreEmpty(@NonNull final Key key) throws Exception {
            final var result = verifier.verify(key, signedBytes, emptyList()).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * If we simply do not have enough signatures to satisfy the key, then the verification should fail.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("Verification fails if we have a good key but insufficient matching signatures")
        void failToVerifyIfSignaturesAreInsufficient(@NonNull final Key key) throws Exception {
            // This time we will get some signatures but NOT enough to satisfy the key. Note that the crypto engine
            // is never even called if this happens.
            final var sigPairs = insufficientSignatures(key);
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * If the signatures simply don't validate, then we fail.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("Verification fails if the signed bytes don't match the selected signatures")
        void failIfCryptoEngineSaysTheSignatureWasBad(@NonNull final Key key) throws Exception {
            // We need to mock out the crypto engine to say the signature was bad, and we need enough signatures
            // to satisfy the key, so we get to the part where the crypto engine is called
            //noinspection unchecked
            doAnswer(SignatureVerifierImplTest::invalid).when(cryptoEngine).verifyAsync(any(List.class));
            final var sigPairs = sufficientSignatures(key);
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * If any one signature for a key in a KeyList is invalid, then the verification must fail.
         */
        @ParameterizedTest
        @MethodSource(value = "provideKeyLists")
        @DisplayName("Verification fails if there is a single bad signature in a KeyList")
        void failIfCryptoEngineSaysASingleSignatureWasBad(@NonNull final Key key) throws Exception {
            // Let only one of the signatures be invalid
            //noinspection unchecked
            doAnswer(SignatureVerifierImplTest::oneInvalidSignature)
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));
            final var sigPairs = sufficientSignatures(key);
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * Threshold keys can be successful even if some of the signatures are bad, as long as "threshold" number of
         * them are good.
         */
        @Test
        @DisplayName("Verification succeeds if there `threshold` good signatures, even if there are bad signatures")
        void failIfMoreThanThresholdBadSignatures() throws Exception {
            // Let there be a few bad signatures but not quite enough to cause a failure
            final var threshold = 3;
            final var thresholdKey = ThresholdKey.newBuilder()
                    .keys(KeyList.newBuilder()
                            .keys(
                                    FAKE_ED25519_KEY_INFOS[0].publicKey(),
                                    FAKE_ED25519_KEY_INFOS[1].publicKey(),
                                    FAKE_ED25519_KEY_INFOS[2].publicKey(),
                                    FAKE_ED25519_KEY_INFOS[3].publicKey(),
                                    FAKE_ED25519_KEY_INFOS[4].publicKey()))
                    .threshold(threshold)
                    .build();
            final var key = Key.newBuilder().thresholdKey(thresholdKey).build();
            //noinspection unchecked
            doAnswer(args -> {
                        //noinspection unchecked
                        final List<TransactionSignature> txSigs = args.getArgument(0, List.class);
                        setVerificationStatus(true, txSigs);
                        for (int i = 0; i < 2; i++) {
                            txSigs.get(i).setSignatureStatus(VerificationStatus.INVALID);
                        }
                        return null;
                    })
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));
            final var sigPairs = allSignatures(key.thresholdKeyOrThrow());
            var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();

            // Now, let there be enough bad signatures to fail.
            //noinspection unchecked
            doAnswer(args -> {
                        //noinspection unchecked
                        final List<TransactionSignature> txSigs = args.getArgument(0, List.class);
                        setVerificationStatus(true, txSigs);
                        for (int i = 0; i < 3; i++) {
                            txSigs.get(i).setSignatureStatus(VerificationStatus.INVALID);
                        }
                        return null;
                    })
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));
            result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * If we have enough valid signatures to satisfy the key, then the verification should succeed.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("Verification succeeds if we have a good key and sufficient matching signatures")
        void verifyIfCryptoEngineSaysTheSignatureWasGood(@NonNull final Key key) throws Exception {
            // Given a sufficient number of signatures to satisfy the key, and a crypto engine that says they are valid,
            // then the verification should succeed.
            //noinspection unchecked
            doAnswer(SignatureVerifierImplTest::valid).when(cryptoEngine).verifyAsync(any(List.class));
            final var sigPairs = sufficientSignatures(key);
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * And maybe we have way more signature than we need. That's good too, as long as we have everything we needed,
         * plus some.
         */
        @ParameterizedTest
        @MethodSource(value = "provideThresholdKeys")
        @DisplayName("Verification succeeds if we have a good key and more than enough matching signatures")
        void verifyIfCryptoEngineSaysTheSignatureWasGood2(@NonNull final Key key) throws Exception {
            //noinspection unchecked
            doAnswer(SignatureVerifierImplTest::valid).when(cryptoEngine).verifyAsync(any(List.class));
            final var sigPairs = allSignatures(key.thresholdKeyOrThrow());
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * Each {@link SignaturePair} has a prefix and a signature and a signature type. When considering signatures
         * that match a primitive key, we only look at those prefixes with a signature type that match the key type.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("Only signatures of the same key type as what is needed are used")
        void onlySignaturesOfTheSameKindUsed(@NonNull final Key key) throws Exception {
            // Given a sufficient number of signatures to satisfy the key, except that some of the signatures are
            // of the wrong type
            final var sigPairs = sufficientSignatures(key);
            final var sigPairToBeModified = sigPairs.get(0);
            final var newSigPair =
                    switch (sigPairToBeModified.signature().kind()) {
                        case ED25519 -> sigPairToBeModified
                                .copyBuilder()
                                .ecdsaSecp256k1(sigPairToBeModified.ed25519OrThrow())
                                .build();
                        case ECDSA_SECP256K1 -> sigPairToBeModified
                                .copyBuilder()
                                .ed25519(sigPairToBeModified.ecdsaSecp256k1OrThrow())
                                .build();
                        default -> throw new AssertionError("Unexpected signature type");
                    };
            sigPairs.set(0, newSigPair);
            // When we verify
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            // Then we find we do not have sufficient signatures
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * The *most* specific signature will always be used. It may be that the signature list has some signatures
         * with prefixes with fewer bytes, and some with more bytes. We should only select those signatures with
         * the *MOST* specific prefix.
         */
        @ParameterizedTest
        @MethodSource(value = "provideMixOfAllKindsOfKeys")
        @DisplayName("The most specific prefix is used to match the signature")
        void verifyMostSpecificPrefixUsed(@NonNull final Key key) throws Exception {
            // Given a sufficient number of signatures to satisfy the key, and then creating some additional signatures
            // with fewer prefix bytes (but earlier in the signature map), and some with longer prefix bytes which
            // don't match, only the most specific are used.
            final var sigPairs = sufficientSignatures(key);
            final var toBeAdded = new ArrayList<SignaturePair>();
            // A function that will nerf the signature bytes
            final Function<SignaturePair, SignaturePair> nerf = (sigPair) -> switch (sigPair.signature()
                    .kind()) {
                case ED25519 -> sigPair.copyBuilder().ed25519(Bytes.EMPTY).build();
                case ECDSA_SECP256K1 -> sigPair.copyBuilder()
                        .ecdsaSecp256k1(Bytes.EMPTY)
                        .build();
                default -> throw new AssertionError("Unexpected signature type");
            };
            // Add in an otherwise good signature that has no bytes
            toBeAdded.add(
                    sigPairs.get(0).copyBuilder().pubKeyPrefix(Bytes.EMPTY).build());
            // Add in otherwise good signatures that have less than PREFIX_LENGTH bytes
            for (final var sigPair : sigPairs) {
                final var prefixLength = PREFIX_LENGTH - 1;
                final var prefix = sigPair.pubKeyPrefix().slice(0, prefixLength);
                toBeAdded.add(
                        nerf.apply(sigPair.copyBuilder().pubKeyPrefix(prefix).build()));
            }
            sigPairs.addAll(0, toBeAdded);
            // And given a crypto engine that will REJECT any signatures that were nerfed. If any of them
            // are ever chosen, then the test fails.
            //noinspection unchecked
            doAnswer(args -> {
                        //noinspection unchecked
                        final List<TransactionSignature> txSigs = args.getArgument(0, List.class);
                        for (final var txSig : txSigs) {
                            txSig.setFuture(completedFuture(null));
                            if (txSig.getSignatureLength() == 0) {
                                txSig.setSignatureStatus(VerificationStatus.INVALID);
                            } else {
                                txSig.setSignatureStatus(VerificationStatus.VALID);
                            }
                        }
                        return null;
                    })
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));

            // When we verify
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            // Then we find success since the correct sigs WERE in there
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * If a signature is missing a public key prefix, then that signature will be selected
         */
        @Test
        void verifySuccessIfPrefixIsMissing() throws Exception {
            //noinspection unchecked
            doAnswer(SignatureVerifierImplTest::valid).when(cryptoEngine).verifyAsync(any(List.class));
            final var key = FAKE_ED25519_KEY_INFOS[0].publicKey();
            final var sigPairs = sufficientSignatures(key).stream()
                    .map(sigPair ->
                            sigPair.copyBuilder().pubKeyPrefix(Bytes.EMPTY).build())
                    .collect(Collectors.toList());
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * Given some complex key structure where the same key is repeated at various levels of depth in the key
         * structure, there is, in the end, only a single signature per key that is verified.
         */
        @Test
        void repeatedKeysAreOnlyVerifiedOnce() throws ExecutionException, InterruptedException {
            final var ed25519Key = FAKE_ED25519_KEY_INFOS[0].publicKey();
            final var ecdsaKey = FAKE_ECDSA_KEY_INFOS[0].publicKey();
            final var key = Key.newBuilder()
                    .keyList(KeyList.newBuilder().keys(ed25519Key, ecdsaKey, thresholdKey(1, ed25519Key, ecdsaKey)))
                    .ed25519(FAKE_ED25519_KEY_INFOS[0].publicKey().ed25519())
                    .ed25519(FAKE_ED25519_KEY_INFOS[0].publicKey().ed25519())
                    .ecdsaSecp256k1(FAKE_ECDSA_KEY_INFOS[0].publicKey().ecdsaSecp256k1())
                    .ecdsaSecp256k1(FAKE_ECDSA_KEY_INFOS[0].publicKey().ecdsaSecp256k1())
                    .thresholdKey(ThresholdKey.newBuilder()
                            .threshold(2)
                            .keys(KeyList.newBuilder()
                                    .keys(
                                            ed25519Key,
                                            ecdsaKey,
                                            thresholdKey(1, ed25519Key, ecdsaKey),
                                            keyList(ed25519Key, ecdsaKey))))
                    .build();

            //noinspection unchecked
            doAnswer(args -> {
                        //noinspection unchecked
                        final List<TransactionSignature> sigs = args.getArgument(0, List.class);
                        setVerificationStatus(sigs.size() == 2, sigs); // Only succeed if we got 2 signatures exactly
                        return null;
                    })
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));
            final var sigPairs = sufficientSignatures(key);
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /**
         * A series of tests to validate that threshold behavior of the threshold key is correct.
         */
        static Stream<Arguments> providePermutations() {
            final var repeated = FAKE_ED25519_KEY_INFOS[0].publicKey();
            final var ecdsa1 = FAKE_ECDSA_KEY_INFOS[0].publicKey();
            final var ecdsa2 = FAKE_ECDSA_KEY_INFOS[1].publicKey();
            final var ecdsa3 = FAKE_ECDSA_KEY_INFOS[2].publicKey();
            return Stream.of(
                    // With a threshold of 1, it doesn't matter what key we provide
                    Arguments.of(1, List.of(ed25519Sig(repeated)), true),
                    Arguments.of(1, List.of(ecdsaSig(ecdsa1)), true),

                    // With a threshold of 2, only the repeated one will succeed if we have a single signature,
                    // but not a non-repeated key
                    Arguments.of(2, List.of(ed25519Sig(repeated)), true),
                    Arguments.of(2, List.of(ecdsaSig(ecdsa1)), false),

                    /// But even a non-repeated key will succeed if we have two signatures
                    Arguments.of(2, List.of(ecdsaSig(ecdsa1), ecdsaSig(ecdsa2)), true),

                    // With a threshold of 3, the repeated one requires one additional sig
                    Arguments.of(3, List.of(ed25519Sig(repeated)), false),
                    Arguments.of(3, List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1)), true),

                    // And the non-repeated ones require all three of them
                    Arguments.of(3, List.of(ecdsaSig(ecdsa1), ecdsaSig(ecdsa2)), false),
                    Arguments.of(3, List.of(ecdsaSig(ecdsa1), ecdsaSig(ecdsa2), ecdsaSig(ecdsa3)), true),

                    // With a threshold of 4, the repeated one requires two additional sigs
                    Arguments.of(4, List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1)), false),
                    Arguments.of(4, List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1), ecdsaSig(ecdsa2)), true),

                    // And the non-repeated ones can no longer succeed on their own
                    Arguments.of(4, List.of(ecdsaSig(ecdsa1), ecdsaSig(ecdsa2), ecdsaSig(ecdsa3)), false),

                    // And with a threshold of 5, they are all required
                    Arguments.of(5, List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1), ecdsaSig(ecdsa2)), false),
                    Arguments.of(5, List.of(ecdsaSig(ecdsa1), ecdsaSig(ecdsa2), ecdsaSig(ecdsa3)), false),
                    Arguments.of(
                            5,
                            List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1), ecdsaSig(ecdsa2), ecdsaSig(ecdsa3)),
                            true),

                    // And repeating signatures doesn't help
                    Arguments.of(
                            5,
                            List.of(
                                    ecdsaSig(ecdsa1),
                                    ecdsaSig(ecdsa1),
                                    ecdsaSig(ecdsa1),
                                    ecdsaSig(ecdsa2),
                                    ecdsaSig(ecdsa3)),
                            false),

                    // A negative threshold counts as 1
                    Arguments.of(-1, List.of(ecdsaSig(ecdsa1)), true),

                    // As does a zero threshold
                    Arguments.of(0, List.of(ecdsaSig(ecdsa1)), true),

                    // And a too-big threshold counts as the number of keys (5 in this case)
                    Arguments.of(1000, List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1), ecdsaSig(ecdsa2)), false),
                    Arguments.of(
                            1000,
                            List.of(ed25519Sig(repeated), ecdsaSig(ecdsa1), ecdsaSig(ecdsa2), ecdsaSig(ecdsa3)),
                            true));
        }

        /**
         * Given a threshold key where the same key is repeated more than once, it should count extra towards
         * validation.
         */
        @ParameterizedTest
        @MethodSource("providePermutations")
        @DisplayName("Repeated keys count extra")
        void repeatedKeysCountExtra(final int threshold, List<SignaturePair> sigPairs, final boolean shouldPass)
                throws ExecutionException, InterruptedException {
            final var repeated = FAKE_ED25519_KEY_INFOS[0].publicKey();
            final var key = thresholdKey(
                    threshold,
                    repeated,
                    repeated,
                    FAKE_ECDSA_KEY_INFOS[0].publicKey(),
                    FAKE_ECDSA_KEY_INFOS[1].publicKey(),
                    FAKE_ECDSA_KEY_INFOS[2].publicKey());

            //noinspection unchecked
            lenient()
                    .doAnswer(SignatureVerifierImplTest::valid)
                    .when(cryptoEngine)
                    .verifyAsync(any(List.class));
            final var result = verifier.verify(key, signedBytes, sigPairs).get();
            assertThat(result.passed()).isEqualTo(shouldPass);
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        @Test
        @DisplayName("Threshold key with no keys fails")
        void thresholdKeyWithNoKeysFails() throws ExecutionException, InterruptedException {
            final var key = thresholdKey(1);
            final var result = verifier.verify(key, signedBytes, List.of()).get();
            assertThat(result.passed()).isFalse();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        @Test
        @DisplayName("KeyList with no keys fails")
        void keyListWithNoKeysFails() throws ExecutionException, InterruptedException {
            final var key = keyList();
            final var result = verifier.verify(key, signedBytes, List.of()).get();
            assertThat(result.passed()).isFalse();
            assertThat(result.key()).isEqualTo(key);
            assertThat(result.hollowAccount()).isNull();
        }

        /** A provider that supplies a mixture of all kinds of keys, all of which are good keys. */
        static Stream<Arguments> provideMixOfAllKindsOfKeys() {
            // FUTURE: Add RSA keys to this list
            return Streams.concat(
                    Stream.of(
                            // Single element keys
                            Arguments.of(named("ED25519", FAKE_ED25519_KEY_INFOS[0].publicKey())),
                            Arguments.of(named("ECDSA_SECP256K1", FAKE_ECDSA_KEY_INFOS[0].publicKey()))),
                    provideKeyLists(),
                    provideThresholdKeys());
        }

        /**
         * A provider specifically for all permutations of a valid key list, including those with duplicate keys
         * and nesting.
         */
        static Stream<Arguments> provideKeyLists() {
            // FUTURE: Add RSA keys to this list
            return Stream.of(
                    // Single element key lists of different key types
                    Arguments.of(named("KeyList(ED25519)", keyList(FAKE_ED25519_KEY_INFOS[0].publicKey()))),
                    Arguments.of(named("KeyList(ECDSA_SECP256K1)", keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey()))),

                    // Multiple element key lists of mixed types (unique keys)
                    Arguments.of(named(
                            "KeyList(ED25519, ECDSA_SECP256K1)",
                            keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[0].publicKey()))),

                    // KeyList with duplicate keys
                    Arguments.of(named(
                            "KeyList(ED25519(dup), ED25519(dup))",
                            keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ED25519_KEY_INFOS[0].publicKey()))),

                    // KeyList with unique keys of the same type
                    Arguments.of(named(
                            "KeyList(ED25519, ED25519)",
                            keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ED25519_KEY_INFOS[1].publicKey()))),
                    Arguments.of(named(
                            "KeyList(ECDSA_SECP256K1, ECDSA_SECP256K1)",
                            keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()))),

                    // Nested key lists
                    Arguments.of(named(
                            "KeyList(ED25519, KeyList(ECDSA_SECP256K1, ECDSA_SECP256K1), ECDSA_SECP256K1)",
                            keyList(
                                    FAKE_ED25519_KEY_INFOS[0].publicKey(),
                                    keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()),
                                    FAKE_ECDSA_KEY_INFOS[2].publicKey()))),

                    // Nested key lists with duplicate keys
                    Arguments.of(named(
                            "KeyList(ED25519, KeyList(ECDSA_SECP256K1(dup), ECDSA_SECP256K1), ECDSA_SECP256K1(dup))",
                            keyList(
                                    FAKE_ED25519_KEY_INFOS[0].publicKey(),
                                    keyList(FAKE_ECDSA_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()),
                                    FAKE_ECDSA_KEY_INFOS[0].publicKey()))),

                    // Key lists with threshold keys
                    Arguments.of(named(
                            "KeyList(ED25519, ThresholdKey(1, ECDSA_SECP256K1, ED25519), ECDSA_SECP256K1)",
                            keyList(
                                    FAKE_ED25519_KEY_INFOS[0].publicKey(),
                                    thresholdKey(
                                            1,
                                            FAKE_ECDSA_KEY_INFOS[0].publicKey(),
                                            FAKE_ED25519_KEY_INFOS[1].publicKey()),
                                    FAKE_ECDSA_KEY_INFOS[2].publicKey()))),

                    // Key lists with threshold keys with duplicates
                    Arguments.of(named(
                            "KeyList(ED25519(dup), ThresholdKey(1, ECDSA_SECP256K1(dup), ED25519(dup)), ECDSA_SECP256K1(dup))",
                            keyList(
                                    FAKE_ED25519_KEY_INFOS[0].publicKey(),
                                    thresholdKey(
                                            1,
                                            FAKE_ECDSA_KEY_INFOS[0].publicKey(),
                                            FAKE_ED25519_KEY_INFOS[0].publicKey()),
                                    FAKE_ECDSA_KEY_INFOS[0].publicKey()))));
        }

        /**
         * A provider specifically for all permutations of a valid threshold key, including those with duplicate keys
         * and nesting.
         */
        static Stream<Arguments> provideThresholdKeys() {
            // FUTURE: Add RSA keys to this list
            return Stream.of(
                    // ThresholdKey with duplicate keys
                    // ThresholdKey with unique keys

                    // Single element threshold keys of different key types
                    Arguments.of(
                            named("ThresholdKey(1, ED25519)", thresholdKey(1, FAKE_ED25519_KEY_INFOS[0].publicKey()))),
                    Arguments.of(thresholdKey(1, FAKE_ECDSA_KEY_INFOS[0].publicKey())),

                    // Multiple element threshold keys of mixed types
                    Arguments.of(thresholdKey(
                            1, FAKE_ED25519_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey())),
                    Arguments.of(thresholdKey(
                            1, FAKE_ED25519_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey())),

                    // Nested Threshold keys
                    Arguments.of(thresholdKey(
                            3,
                            keyList(FAKE_ED25519_KEY_INFOS[0].publicKey(), FAKE_ECDSA_KEY_INFOS[0].publicKey()),
                            FAKE_ED25519_KEY_INFOS[2].publicKey(),
                            FAKE_ECDSA_KEY_INFOS[2].publicKey(),
                            thresholdKey(
                                    1, FAKE_ECDSA_KEY_INFOS[1].publicKey(), FAKE_ECDSA_KEY_INFOS[1].publicKey()))));
        }

        /** Convenience method for creating a key list */
        private static Key keyList(Key... keys) {
            return Key.newBuilder().keyList(KeyList.newBuilder().keys(keys)).build();
        }

        /** Convenience method for creating a threshold key */
        private static Key thresholdKey(int threshold, Key... keys) {
            return Key.newBuilder()
                    .thresholdKey(ThresholdKey.newBuilder()
                            .keys(KeyList.newBuilder().keys(keys))
                            .threshold(threshold))
                    .build();
        }

        /**
         * Based on the kind of key, returns a list of {@link SignaturePair}s that will pass verification.
         * This list will have *just barely* enough signatures to pass (i.e. they must all pass verification for the
         * key to pass, if even a single key fails, the overall validation will fail).
         *
         * <p>Note that if keys are repeated, only a single signature will be returned for that key. All signatures
         * will have a prefix of {@link #PREFIX_LENGTH} bytes.
         */
        private static List<SignaturePair> sufficientSignatures(@NonNull final Key key) {
            final var list =
                    switch (key.key().kind()) {
                        case KEY_LIST -> sufficientSignatures(key.keyListOrThrow());
                        case THRESHOLD_KEY -> sufficientSignatures(key.thresholdKeyOrThrow());
                        case ED25519 -> List.of(ed25519Sig(key));
                        case ECDSA_SECP256K1 -> List.of(ecdsaSig(key));
                        default -> throw new IllegalArgumentException(
                                "Unsupported key type: " + key.key().kind());
                    };
            return list.stream().distinct().collect(Collectors.toList()); // No duplicate signatures please!
        }

        /** Utility to create a signature for this ED25519 key */
        private static SignaturePair ed25519Sig(@NonNull final Key key) {
            final Bytes bytes = key.key().as();
            return SignaturePair.newBuilder()
                    .pubKeyPrefix(bytes.getBytes(0, PREFIX_LENGTH))
                    .ed25519(bytes)
                    .build();
        }

        /** Utility to create a signature for this ECDSA_SECP256K1 key */
        private static SignaturePair ecdsaSig(@NonNull final Key key) {
            final Bytes bytes = key.key().as();
            return SignaturePair.newBuilder()
                    .pubKeyPrefix(bytes.getBytes(0, PREFIX_LENGTH))
                    .ecdsaSecp256k1(bytes)
                    .build();
        }

        /** Creates a {@link SignaturePair} for each unique key in the key list */
        private static List<SignaturePair> sufficientSignatures(@NonNull final KeyList key) {
            return key.keysOrThrow().stream()
                    .map(KeyBasedTest::sufficientSignatures)
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }

        /**
         * Creates a {@link SignaturePair} for each unique key in the threshold key UP TO the threshold, but no more.
         */
        private static List<SignaturePair> sufficientSignatures(@NonNull final ThresholdKey key) {
            final var sigPairs = sufficientSignatures(key.keysOrThrow());
            final var numToRemove = sigPairs.size() - key.threshold();
            if (numToRemove > 0) {
                sigPairs.subList(0, numToRemove).clear();
            }
            return sigPairs;
        }

        /** Creates a {@link SignaturePair} for each unique key in the threshold key */
        private static List<SignaturePair> allSignatures(@NonNull final ThresholdKey key) {
            return sufficientSignatures(key.keysOrThrow());
        }

        /** Creates a list of {@link SignaturePair}s that are insufficient to pass verification */
        private static List<SignaturePair> insufficientSignatures(@NonNull final Key key) {
            final var sigPairs = new ArrayList<>(sufficientSignatures(key));
            sigPairs.remove(0);
            return sigPairs;
        }
    }

    @Nested
    @DisplayName("Hollow Account based Verification")
    final class HollowAccountBasedTest {

        private List<SignaturePair> signatures(@NonNull final TestKeyInfo key) {
            return new ArrayList<>(List.of(SignaturePair.newBuilder()
                    .pubKeyPrefix(key.publicKey().ecdsaSecp256k1OrThrow())
                    .ecdsaSecp256k1(randomBytes(64)) // For the tests it doesn't matter since crypto engine is mocked
                    .build()));
        }

        private Key uncompressed(Key key) {
            final var ecdsaBytes = key.ecdsaSecp256k1OrThrow();
            final var array = new byte[(int) ecdsaBytes.length()];
            ecdsaBytes.getBytes(0, array);
            return Key.newBuilder()
                    .ecdsaSecp256k1(Bytes.wrap(MiscCryptoUtils.decompressSecp256k1(array)))
                    .build();
        }

        /** As with key verification, with hollow account verification, an empty list of signatures should fail. */
        @Test
        @DisplayName("Cannot verify hollow account when the signature list is empty")
        void failToVerifyIfSignaturesAreEmpty() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var result =
                    verifier.verify(hollowAccount, signedBytes, emptyList()).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        /** If the signed bytes don't match the signatures, then the verification should fail. */
        @Test
        @DisplayName("If the hollow account signature was found but invalid then fail")
        void failIfCryptoEngineSaysTheSignatureWasBad() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            doAnswer(SignatureVerifierImplTest::invalid)
                    .when(cryptoEngine)
                    .verifyAsync(any(TransactionSignature.class));
            final var sigPairs = signatures(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0]);
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isEqualTo(uncompressed(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].publicKey()));
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        /** If we simply do not have the right signature, then it should fail. */
        @Test
        @DisplayName("If the prefix matching the key was not found")
        void failToVerifyIfPrefixNotFound() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var sigPairs = signatures(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[1]); // Not the one we are looking for!
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        /** If the FULL key is not set as the prefix, then it won't be found either. */
        @Test
        @DisplayName("If the prefix was not the _full_ key then it will fail")
        void failToVerifyIfPrefixDoesNotMatchEntireKey() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            // This sig pair would work, except we're truncating it
            final var sigPairs = signatures(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0]);
            final var sig = sigPairs.get(0).pubKeyPrefix();
            sigPairs.set(
                    0,
                    sigPairs.get(0).copyBuilder().pubKeyPrefix(sig.slice(0, 10)).build());
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        /** If the signature bytes cannot be decompressed, then the signature is skipped */
        @Test
        @DisplayName("The signature has to be SECP256K1 compressed bytes")
        void failToVerifyIfPrefixDoesNotHaveProperCompressedBytes() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var signatureByteArray = randomByteArray(33);
            signatureByteArray[0] = (byte) 0xAB; // Illegal first char
            final var sigPairs = List.of(SignaturePair.newBuilder()
                    .pubKeyPrefix(Bytes.wrap(signatureByteArray))
                    .ecdsaSecp256k1(randomBytes(64))
                    .build());
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        @Test
        @DisplayName("If the signature is found, then it should verify")
        void verifyHappyPath() throws Exception {
            doAnswer(SignatureVerifierImplTest::valid).when(cryptoEngine).verifyAsync(any(TransactionSignature.class));
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var sigPairs = signatures(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0]);
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.passed()).isTrue();
            assertThat(result.key()).isEqualTo(uncompressed(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].publicKey()));
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        @Test
        @DisplayName("If the public key prefix is missing, then it will not be selected.")
        void verifyFailsIfTheOnlySigHasEmptyPrefix() throws Exception {
            final var hollowAccount = Account.newBuilder()
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var sigPairs = List.of(SignaturePair.newBuilder()
                    .pubKeyPrefix(Bytes.EMPTY)
                    .ecdsaSecp256k1(randomBytes(64))
                    .build());
            final var result =
                    verifier.verify(hollowAccount, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(hollowAccount);
        }

        @Test
        @DisplayName("If the account is not hollow, it will fail validation")
        void verifyFailsIfTheAccountIsNotHollow() throws Exception {
            final var account = Account.newBuilder()
                    .key(uncompressed(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].publicKey()))
                    .alias(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0].alias())
                    .build();
            final var sigPairs = signatures(FAKE_ECDSA_WITH_ALIAS_KEY_INFOS[0]);
            final var result = verifier.verify(account, signedBytes, sigPairs).get();
            assertThat(result.failed()).isTrue();
            assertThat(result.key()).isNull();
            assertThat(result.hollowAccount()).isEqualTo(account);
        }
    }

    private static Void oneInvalidSignature(InvocationOnMock args) {
        //noinspection unchecked
        final List<TransactionSignature> txSigs = args.getArgument(0, List.class);
        setVerificationStatus(true, txSigs);
        txSigs.get(0).setSignatureStatus(VerificationStatus.INVALID);
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Void valid(InvocationOnMock args) {
        final var arg = args.getArgument(0);
        if (arg instanceof List list) {
            setVerificationStatus(true, list);
        } else {
            setVerificationStatus(true, List.of((TransactionSignature) arg));
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Void invalid(InvocationOnMock args) {
        final var arg = args.getArgument(0);
        if (arg instanceof List list) {
            setVerificationStatus(false, list);
        } else {
            setVerificationStatus(false, List.of((TransactionSignature) arg));
        }
        return null;
    }

    private static void setVerificationStatus(boolean result, List<TransactionSignature> list) {
        for (final var sig : list) {
            sig.setFuture(completedFuture(null));
            sig.setSignatureStatus(result ? VerificationStatus.VALID : VerificationStatus.INVALID);
        }
    }
}
