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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link HandleContextVerifier} that delegates to a parent {@link HandleContextVerifier} observing a provided
 * {@link VerificationAssistant}.
 */
public class ChildHandleContextVerifier implements HandleContextVerifier {

    private final HandleContextVerifier parent;
    private final VerificationAssistant parentCallback;

    /**
     * Constructs a {@link ChildHandleContextVerifier}
     *
     * @param parent the parent {@link HandleContextVerifier}
     * @param parentCallback the parent {@link VerificationAssistant}
     */
    public ChildHandleContextVerifier(
            @NonNull final HandleContextVerifier parent, @NonNull final VerificationAssistant parentCallback) {
        this.parent = requireNonNull(parent, "parent must not be null");
        this.parentCallback = requireNonNull(parentCallback, "parentCallback must not be null");
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return parent.verificationFor(key, parentCallback);
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(
            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
        requireNonNull(key, "key must not be null");
        requireNonNull(callback, "callback must not be null");
        final VerificationAssistant composedCallback = (key1, signatureVerification) -> {
            final var intermediateVerification = parentCallback.test(key1, signatureVerification)
                    ? SignatureVerificationImpl.passedVerification(key1)
                    : SignatureVerificationImpl.failedVerification(key1);
            return callback.test(key1, intermediateVerification);
        };
        return parent.verificationFor(key, composedCallback);
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        return parent.verificationFor(evmAlias);
    }
}
