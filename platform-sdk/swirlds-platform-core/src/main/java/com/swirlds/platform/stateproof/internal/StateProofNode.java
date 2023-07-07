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

package com.swirlds.platform.stateproof.internal;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A node in a state proof tree.
 */
public interface StateProofNode extends SelfSerializable {

    /**
     * Get the bytes that this node contributes to its parent's hash.
     *
     * @param cryptography provides cryptographic services
     * @return the bytes that this node contributes to its parent's hash
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    @NonNull
    byte[] getHashableBytes(@NonNull final Cryptography cryptography);

    /**
     * Get all payloads at or below this node.
     *
     * @return all payloads at or below this node
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    @NonNull
    List<MerkleLeaf> getPayloads();
}
