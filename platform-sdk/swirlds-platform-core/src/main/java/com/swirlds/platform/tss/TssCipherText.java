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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.ecdh.EcdhPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public record TssCipherText(@NonNull GroupElement[] c1, @NonNull Map<TssShareId, GroupElement[]> c2) {
    // The length of the above arrays must be CHUNKS_PER_SHARE
    public static final int CHUNKS_PER_SHARE = 16;
    // This is stored in the state and needs to be in protobuf.

    public List<TssPrivateShare> decryptPrivateShares(
            @NonNull final EcdhPrivateKey ecdhPrivateKey, @NonNull final List<TssShareId> shareIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<TssPublicShare> getAllPublicShares() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<TssPublicShare> getPublicShares(@NonNull final List<TssShareId> shareIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    //return bytes for serialization.
}
