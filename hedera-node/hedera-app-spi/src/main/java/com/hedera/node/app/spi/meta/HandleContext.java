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

package com.hedera.node.app.spi.meta;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Bundles up the in-handle application context required by {@link TransactionHandler}
 * implementations.
 *
 * <p>At present, only supplies the context needed for Consensus Service handlers in the
 * limited form described by https://github.com/hashgraph/hedera-services/issues/4945.
 */
public interface HandleContext {
    /**
     * Returns the current consensus time.
     *
     * @return the current consensus time
     */
    Instant consensusNow();

    /**
     * Returns a supplier of the next entity number, for use by handlers that create entities.
     *
     * @return a supplier of the next entity number
     */
    LongSupplier newEntityNumSupplier();

    /**
     * Returns the validator for attributes of entities created or updated by handlers.
     *
     * @return the validator for attributes
     */
    AttributeValidator attributeValidator();

    /**
     * Returns the validator for expiry metadata (both explicit expiration times and
     * auto-renew configuration) of entities created or updated by handlers.
     *
     * @return the validator for expiry metadata
     */
    ExpiryValidator expiryValidator();

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during
     * pre-handle, then there will be no corresponding {@link SignatureVerification}. If the key was provided during
     * pre-handle, then the corresponding {@link SignatureVerification} will be returned with the result of that
     * verification operation.
     *
     * @param key the key to get the verification for
     * @return the verification for the given key, or {@code null} if no such key was provided during pre-handle
     */
    @Nullable
    SignatureVerification verificationFor(@NonNull final Key key);

    /**
     * Gets the {@link SignatureVerification} for the given EVM address alias. If this alias was not provided during
     * pre-handle, then there will be no corresponding {@link SignatureVerification}. If the alias was provided during
     * pre-handle, then the corresponding {@link SignatureVerification} will be returned with the result of that
     * verification operation. If during signature verification a key was extracted then it will be made available in
     * the {@link SignatureVerification}.
     *
     * @param alias the alias to get the verification for
     * @return the verification for the given alias, or {@code null} if no such alias was provided during pre-handle
     */
    @Nullable
    SignatureVerification verificationFor(@NonNull final Bytes alias);
}
