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

package com.swirlds.platform.tss.bls;

import com.swirlds.platform.tss.TssPublicKey;
import com.swirlds.platform.tss.TssShareId;
import com.swirlds.platform.tss.TssSignature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS implementation of a TSS signature.
 *
 * @param shareId   the ID of the share that produced the signature
 * @param signature the signature. TODO: this should be a group element, but it's a long for now so it compiles
 */
public record BlsSignature(@NonNull TssShareId shareId, @NonNull Long signature) implements TssSignature {
    @Override
    public boolean verifySignature(@NonNull final TssPublicKey publicKey, @NonNull final byte[] message) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
