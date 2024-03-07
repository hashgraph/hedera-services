/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.protocol;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.network.communication.handshake.HashCompareHandshake;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Implementation of a protocol factory for hash compare handshake
 */
public class HashCompareHandshakeProtocolFactory implements ProtocolFactory {

    private final Hash hash;

    /**
     * Constructor
     *
     * @param hash this node's hash
     */
    public HashCompareHandshakeProtocolFactory(@NonNull final Hash hash) {
        this.hash = Objects.requireNonNull(hash);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public HashCompareHandshake build(final boolean throwOnMismatch) {
        return new HashCompareHandshake(hash, throwOnMismatch);
    }
}
