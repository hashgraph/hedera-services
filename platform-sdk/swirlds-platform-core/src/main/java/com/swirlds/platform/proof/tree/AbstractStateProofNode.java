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

package com.swirlds.platform.proof.tree;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Boilerplate for state proof tree nodes.
 */
public abstract class AbstractStateProofNode implements StateProofNode {

    private byte[] hashableBytes;

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public final byte[] getHashableBytes() {
        if (hashableBytes == null) {
            throw new IllegalStateException("Hashable bytes have not been computed");
        }
        return hashableBytes;
    }

    /**
     * Set the hashable bytes for this node.
     *
     * @param hashableBytes the hashable bytes
     */
    protected void setHashableBytes(@NonNull byte[] hashableBytes) {
        Objects.requireNonNull(hashableBytes);
        if (this.hashableBytes != null) {
            throw new IllegalArgumentException("setHashableBytes() called more than once");
        }
        this.hashableBytes = hashableBytes;
    }
}
