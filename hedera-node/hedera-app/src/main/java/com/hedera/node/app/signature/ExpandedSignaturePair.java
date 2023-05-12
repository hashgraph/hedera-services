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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Represents a {@link SignaturePair} where the {@link SignaturePair#pubKeyPrefix()} has been fully "expanded" to a
 * full, uncompressed, public key.
 *
 * @param key The fully expanded public key
 * @param hollowAccount If the sigPair applied to a hollow account, then that account will be provided here
 * @param sigPair The original signature pair
 */
public record ExpandedSignaturePair(@NonNull Key key, @Nullable Account hollowAccount, @NonNull SignaturePair sigPair) {
    /**
     * Gets the {@link Bytes} representing the fully expanded public key.
     * @return The key bytes.
     */
    @NonNull
    public Bytes keyBytes() {
        return sigPair.pubKeyPrefix();
    }

    /**
     * Gets the {@link Bytes} representing the signature signed by the private key matching the fully expanded public
     * key.
     * @return The signature bytes.
     */
    @NonNull
    public Bytes signature() {
        return sigPair.signature().as();
    }
}
