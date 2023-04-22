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

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
     * will create {@link SignatureVerification} instances based on some concrete key. In those cases, they will be made
     * available here. In the case of a "hollow" account, where the key is not known, the handler will provide the
     * EVM compatible alias for the hollow account. The signature checking code will look for a corresponding signature.
     * If it finds one, it will extract the key and make it available here. If it cannot find a corresponding signature,
     * then this key will be null.
     *
     * @return The key that was used with this signature check, or null if the key is not known.
     */
    @Nullable
    Key key();

    /**
     * Gets the EVM compatible alias for the key to be used during signature verification. This is only used by
     * hollow accounts, where they do not have a key on the account, but one can be provided by the signed transaction
     * itself.
     *
     * @return An EVM compatible alias for the key to be used during signature verification, or null if a key was
     * provided.
     */
    @Nullable
    default Bytes alias() {
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
}
