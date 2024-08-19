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

import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Helper class that contains all functionality for verifying signatures during handle.
 */
public interface AppKeyVerifier extends KeyVerifier {

    /**
     * Look for a {@link SignatureVerification} that applies to the given hollow account.
     * @param evmAlias The evm alias to lookup verification for.
     * @return The {@link SignatureVerification} for the given hollow account.
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Bytes evmAlias);

    /**
     * Gets the number of signatures verified for this transaction.
     *
     * @return the number of signatures verified for this transaction. Non-negative.
     */
    int numSignaturesVerified();
}
