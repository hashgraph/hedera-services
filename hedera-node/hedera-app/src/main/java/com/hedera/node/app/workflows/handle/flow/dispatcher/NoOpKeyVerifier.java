/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NoOpKeyVerifier implements KeyVerifier {
    @Inject
    public NoOpKeyVerifier() {}

    private static final SignatureVerification PASSED_VERIFICATION =
            new SignatureVerificationImpl(Key.DEFAULT, Bytes.EMPTY, true);

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Key key) {
        return PASSED_VERIFICATION;
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(
            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
        return PASSED_VERIFICATION;
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        return PASSED_VERIFICATION;
    }

    @Override
    public int numSignaturesVerified() {
        return 0;
    }
}
