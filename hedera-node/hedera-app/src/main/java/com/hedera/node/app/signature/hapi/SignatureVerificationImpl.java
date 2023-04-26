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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of {@link SignatureVerification}, created by {@link SignatureVerificationFuture}. This class
 * should have been package private, but {@link com.hedera.node.app.workflows.handle.AdaptedMonoProcessLogic} needs
 * access to the {@link #txSigs()}. Once we don't have to use the adapter to mono anymore, we can remove the
 * {@link #txSigs()} and make this package private again.
 *
 * @param key The key
 * @param hollowAccount The hollow account, if any
 * @param txSigs The {@link TransactionSignature}s created by the verifier.
 * @param passed Whether the verification passed (VALID) or not.
 */
public record SignatureVerificationImpl(
        @Nullable Key key, @Nullable Account hollowAccount, @NonNull List<TransactionSignature> txSigs, boolean passed)
        implements SignatureVerification {

    /**
     * Create a {@link SignatureVerificationImpl} because the given {@link Key} was invalid (did not pass signature
     * check).
     */
    public static SignatureVerification invalid(@NonNull Key key) {
        return new SignatureVerificationImpl(key, null, Collections.emptyList(), false);
    }

    /**
     * Create a {@link SignatureVerificationImpl} because the given hollow {@link Account} was invalid (did not pass
     * signature check).
     */
    public static SignatureVerification invalid(@NonNull Account hollowAccount) {
        return new SignatureVerificationImpl(null, hollowAccount, Collections.emptyList(), false);
    }

    /**
     * Create a {@link SignatureVerificationImpl} because the given {@link Key} passed signature verification.
     */
    public static SignatureVerification valid(@NonNull Key key, @NonNull List<TransactionSignature> txSigs) {
        return new SignatureVerificationImpl(key, null, txSigs, true);
    }

    /**
     * Create a {@link SignatureVerificationImpl} because the given hollow {@link Account} passed signature verification
     */
    public static SignatureVerification valid(
            @NonNull Key key, @NonNull Account hollowAccount, @NonNull List<TransactionSignature> txSigs) {
        return new SignatureVerificationImpl(key, hollowAccount, txSigs, true);
    }
}
