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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * Helper class that contains all functionality for verifying signatures during handle.
 */
public class HandleContextVerifier {

    private final Map<Key, SignatureVerification> keyVerifications;

    /**
     * Creates a {@link HandleContextVerifier}
     *
     * @param keyVerifications A {@link Map} with all data for required signatures
     */
    public HandleContextVerifier(@NonNull final Map<Key, SignatureVerification> keyVerifications) {
        this.keyVerifications = requireNonNull(keyVerifications, "keyVerifications must not be null");
    }

    /**
     * Gets the {@link SignatureVerification} for a {@link Key}
     *
     * @param key The {@link Key} to get the {@link SignatureVerification} for
     * @return The {@link SignatureVerification} if found, otherwise {@code null}
     * @throws NullPointerException If {@code key} is {@code null}
     */
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return keyVerifications.get(key);
    }

    /**
     * Gets the {@link SignatureVerification} for a hollow account
     *
     * @param evmAlias The evm-alias of the hollow account
     * @return The {@link SignatureVerification} if found, otherwise {@code null}
     * @throws NullPointerException If {@code evmAlias} is {@code null}
     */
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        if (evmAlias.length() == 20) {
            for (final var result : keyVerifications.values()) {
                final var account = result.evmAlias();
                if (account != null && evmAlias.matchesPrefix(account)) {
                    return result;
                }
            }
        }
        return null;
    }
}
