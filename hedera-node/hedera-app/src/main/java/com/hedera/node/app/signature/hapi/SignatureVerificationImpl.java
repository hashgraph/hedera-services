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
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

// Created by SignatureVerificationFuture when it has the final result.
public record SignatureVerificationImpl(@Nullable Key key, @Nullable Bytes evmAddress, boolean passed)
        implements SignatureVerification {

    public static SignatureVerification invalid(@NonNull Key key) {
        return new SignatureVerificationImpl(key, null, false);
    }

    public static SignatureVerification invalid(@NonNull Bytes evmAddress) {
        return new SignatureVerificationImpl(null, evmAddress, false);
    }

    public static SignatureVerification valid(@NonNull Key key) {
        return new SignatureVerificationImpl(key, null, true);
    }

    public static SignatureVerification valid(@NonNull Key key, @NonNull Bytes evmAddress) {
        return new SignatureVerificationImpl(key, evmAddress, true);
    }
}
