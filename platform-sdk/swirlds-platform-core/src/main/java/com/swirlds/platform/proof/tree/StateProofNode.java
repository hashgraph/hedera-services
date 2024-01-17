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

import com.swirlds.common.crypto.Cryptography;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;

/**
 * A node in a state proof tree.
 */
public interface StateProofNode {

    /**
     * Compute the bytes that this node contributes to its parent's hash, and store those bytes and return them when
     * {@link #getHashableBytes()} is called. This method is called on each state proof node in a post-ordered depth
     * first traversal of the state proof tree.
     *
     * @param cryptography provides cryptographic primitives
     * @param digest       builds running hashes
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    void computeHashableBytes(@NonNull final Cryptography cryptography, @NonNull final MessageDigest digest);

    /**
     * Get the bytes that this node contributes to its parent's hash. Guaranteed to be called after
     * {@link #computeHashableBytes(Cryptography, MessageDigest)}.
     *
     * @return the bytes that this node contributes to its parent's hash
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    @NonNull
    byte[] getHashableBytes();
}
