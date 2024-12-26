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

package com.hedera.node.app.hints.impl;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

public interface HintsKeyAccessor {
    /**
     * Returns the private part of the hinTS BLS key, creating it if necessary.
     * @param constructionId the active construction ID
     * @param message the message to sign
     * @return the signature, if this node contributed a BLS key for the given construction ID
     */
    Optional<Bytes> signWithBlsPrivateKey(long constructionId, @NonNull Bytes message);

    /**
     * Returns the public part of the hinTS BLS key, creating it if necessary.
     * @return the hinTS key
     */
    BlsPublicKey getOrCreateBlsPublicKey();
}
