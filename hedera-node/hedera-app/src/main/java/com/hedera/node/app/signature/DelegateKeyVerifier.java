/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * A {@link AppKeyVerifier} that delegates resolves complex keys and passes checks for primitive keys
 * to a provided {@link Predicate}-verifier.
 */
public class DelegateKeyVerifier implements AppKeyVerifier {

    private final Predicate<Key> baseVerifier;

    /**
     * Constructs a {@link DelegateKeyVerifier}
     *
     * @param baseVerifier the base verifier
     */
    public DelegateKeyVerifier(@NonNull final Predicate<Key> baseVerifier) {
        this.baseVerifier = requireNonNull(baseVerifier, "baseVerifier must not be null");
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return doVerification(key, baseVerifier);
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(
            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
        requireNonNull(key, "key must not be null");
        requireNonNull(callback, "callback must not be null");
        final Predicate<Key> composedVerifier = key1 -> {
            final var intermediateVerification = baseVerifier.test(key1)
                    ? SignatureVerificationImpl.passedVerification(key1)
                    : SignatureVerificationImpl.failedVerification(key1);
            return callback.test(key1, intermediateVerification);
        };
        return doVerification(key, composedVerifier);
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        return failedVerification(evmAlias);
    }

    @Override
    public int numSignaturesVerified() {
        return 0;
    }

    /**
     * Does a complex verification of a {@link Key} using the provided {@link Predicate} to check primitive keys.
     *
     * @param key the {@link Key} to verify
     * @param primitiveVerifier the {@link Predicate} to use to verify primitive keys
     * @return the {@link SignatureVerification} result
     */
    @NonNull
    private static SignatureVerification doVerification(
            @NonNull final Key key, @NonNull final Predicate<Key> primitiveVerifier) {
        final var result =
                switch (key.key().kind()) {
                    case KEY_LIST -> {
                        final var keys = key.keyListOrThrow().keys();
                        boolean failed = keys.isEmpty(); // an empty keyList fails by definition
                        for (final var childKey : keys) {
                            failed |=
                                    doVerification(childKey, primitiveVerifier).failed();
                        }
                        yield !failed;
                    }
                    case THRESHOLD_KEY -> {
                        final var thresholdKey = key.thresholdKeyOrThrow();
                        final var keyList = thresholdKey.keysOrElse(KeyList.DEFAULT);
                        final var keys = keyList.keys();
                        final var threshold = thresholdKey.threshold();
                        final var clampedThreshold = Math.max(1, Math.min(threshold, keys.size()));
                        var passed = 0;
                        for (final var childKey : keys) {
                            if (doVerification(childKey, primitiveVerifier).passed()) {
                                passed++;
                            }
                        }
                        yield passed >= clampedThreshold;
                    }
                    default -> primitiveVerifier.test(key);
                };
        return result ? passedVerification(key) : failedVerification(key);
    }
}
