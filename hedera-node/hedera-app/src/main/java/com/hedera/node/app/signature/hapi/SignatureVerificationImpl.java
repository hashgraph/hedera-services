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

// Created by SignatureVerificationFuture when it has the final result.
// txSigs is temporary. After mono-service is gone, it won't be needed anymore
public record SignatureVerificationImpl(@Nullable Key key, @Nullable Account hollowAccount, @NonNull List<TransactionSignature> txSigs, boolean passed)
        implements SignatureVerification {

    public static SignatureVerification invalid(@NonNull Key key) {
        return new SignatureVerificationImpl(key, null, Collections.emptyList(), false);
    }

    public static SignatureVerification invalid(@NonNull Account hollowAccount) {
        return new SignatureVerificationImpl(null, hollowAccount, Collections.emptyList(), false);
    }

    public static SignatureVerification valid(@NonNull Key key, @NonNull List<TransactionSignature> txSigs) {
        return new SignatureVerificationImpl(key, null, txSigs, true);
    }

    public static SignatureVerification valid(@NonNull Key key, @NonNull Account hollowAccount, @NonNull List<TransactionSignature> txSigs) {
        return new SignatureVerificationImpl(key, hollowAccount, txSigs, true);
    }
}
