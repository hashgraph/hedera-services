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

package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CompletableFuture;

/** A simple implementation of {@link SignatureVerificationFuture} that is backed by a {@link CompletableFuture} */
public final class FakeSignatureVerificationFuture extends CompletableFuture<SignatureVerification>
        implements SignatureVerificationFuture {

    private final SignatureVerification verification;

    public FakeSignatureVerificationFuture(@NonNull final SignatureVerification verification) {
        this.verification = verification;
        super.complete(verification);
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    public static FakeSignatureVerificationFuture goodFuture(@NonNull final Key key) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, null, true));
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    public static FakeSignatureVerificationFuture goodFuture(@NonNull final Key key, @NonNull final Account account) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, account.alias(), true));
    }

    /** Convenience method for creating a SignatureVerificationFuture that fails */
    public static FakeSignatureVerificationFuture badFuture(@NonNull final Key key) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, null, false));
    }

    /** Convenience method for creating a SignatureVerificationFuture that passes */
    public static FakeSignatureVerificationFuture badFuture(@NonNull final Key key, @NonNull final Account account) {
        return new FakeSignatureVerificationFuture(new SignatureVerificationImpl(key, account.alias(), false));
    }

    @Nullable
    @Override
    public Bytes evmAlias() {
        return verification.evmAlias();
    }

    @NonNull
    @Override
    public Key key() {
        return requireNonNull(verification.key());
    }
}
