/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.keys;

import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;

/** A replacement class for legacy {@link com.hedera.services.legacy.core.jproto.JKey}.
 * It represents different types of {@link Key}s supported in the codebase.
 * NOTE: This interface will implement {@link VirtualValue} once JKey is removed.
 */
public interface HederaKey {
    /**
     * Returns if the key is primitive key. Currently, supported primitive key types
     * are ECDSA_SECP256K1 keys or ED25519 Keys
     * @return true if the keys is primitive, false otherwise
     */
    boolean isPrimitive();

    /**
     * Returns if the key is empty
     * @return true if the key is empty, false otherwise
     */
    boolean isEmpty();

    /**
     * Returns if the given key is valid.
     * @return true if valid, false otherwise
     */
    boolean isValid();

    /**
     * Performs a left-to-right DFS of the primitive keys in a HederaKey, offering each
     * simple key to the provided {@link Consumer}.
     * @param actionOnSimpleKey the logic to apply to each visited simple key.
     */
    default void visitPrimitiveKeys(final Consumer<HederaKey> actionOnSimpleKey) {
        actionOnSimpleKey.accept(this);
    }
}
