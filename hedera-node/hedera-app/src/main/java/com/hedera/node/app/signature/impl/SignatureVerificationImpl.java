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

package com.hedera.node.app.signature.impl;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link SignatureVerification}.
 *
 * @param key The key, if any
 * @param evmAlias The evm alias of the key if supplied.
 * @param passed Whether the verification passed (VALID) or not.
 */
public record SignatureVerificationImpl(@Nullable Key key, @Nullable Bytes evmAlias, boolean passed)
        implements SignatureVerification {

    public static SignatureVerification passedVerification(@NonNull final Key key) {
        return new SignatureVerificationImpl(key, null, true);
    }

    public static SignatureVerification failedVerification(@NonNull final Key key) {
        return new SignatureVerificationImpl(key, null, false);
    }

    public static SignatureVerification passedVerification(@NonNull final Bytes evmAlias) {
        return new SignatureVerificationImpl(null, evmAlias, true);
    }

    public static SignatureVerification failedVerification(@NonNull final Bytes evmAlias) {
        return new SignatureVerificationImpl(null, evmAlias, false);
    }
}
