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

package com.hedera.node.app.spi.signatures;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * During the "pre-handle" phase of a transaction, the node will perform signature verification on the transaction.
 * Each {@link com.hedera.node.app.spi.workflows.TransactionHandler} has a chance to "gather" required non-payer
 * keys that must be used for signing. The application will then take these and perform asynchronous signature
 * verification. The result of this will be made available to the transaction handler during the "handle" phase
 * as a {@link SignatureVerification}.
 */
public interface SignatureVerification {
    /**
     * Gets the key that was used with this signature check.
     *
     * <p>In most cases, the service implementation's {@link com.hedera.node.app.spi.workflows.TransactionHandler}s
     * will require signatures based on some concrete key. In those cases, they will be made available here. In the case
     * of a "hollow" account, where the key is not known, the handler will provide the hollow {@link Account}. The
     * signature checking code will look for a corresponding signature. If it finds one, it will extract the key and
     * make it available here. If it cannot find a corresponding signature, then this key will be null.
     *
     * @return The key that was used with this signature check, or null if the key is not known.
     */
    @Nullable
    Key key();

    /**
     * Gets the EVM alias for the {@link #key()}, if, and only if, the key is an ECDSA_SECP256K1 key.
     *
     * @return The evm alias, or null the key is not of the correct type.
     */
    @Nullable
    default Bytes evmAlias() {
        return null;
    }

    /**
     * Gets whether this signature was verified.
     *
     * @return {@code true} if the signature was verified, {@code false} otherwise
     */
    boolean passed();

    /**
     * Gets whether this signature was not verified.
     *
     * @return {@code false} if the signature was verified, {@code true} otherwise
     */
    default boolean failed() {
        return !passed();
    }

    /**
     * Convenience method to create a SignatureVerification that failed
     *
     * @param key The key for which verification failed
     */
    @NonNull
    static SignatureVerification failedVerification(@NonNull final Key key) {
        requireNonNull(key, "Key must not be null");
        return new SignatureVerification() {
            @NonNull
            @Override
            public Key key() {
                return key;
            }

            @Override
            public boolean passed() {
                return false;
            }
        };
    }

    /**
     * Convenience method to create a SignatureVerification for a hollow account that failed
     *
     * @param evmAlias The alias for which verification failed
     */
    static SignatureVerification failedVerification(@NonNull final Bytes evmAlias) {
        return new SignatureVerification() {
            @Nullable
            @Override
            public Key key() {
                return null;
            }

            @NonNull
            @Override
            public Bytes evmAlias() {
                return evmAlias;
            }

            @Override
            public boolean passed() {
                return false;
            }
        };
    }
}
