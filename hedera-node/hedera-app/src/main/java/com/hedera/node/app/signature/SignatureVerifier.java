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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Asynchronously verifies signatures.
 */
public interface SignatureVerifier {
    /**
     * Asynchronously verifies that the given {@code sigPairs} match the given {@code signedBytes}.
     *
     * @param signedBytes The signed bytes to verify
     * @param sigPairs The matching set of signatures to be verified
     * @return A {@link Set} of {@link Future}s, one per {@link ExpandedSignaturePair}.
     */
    @NonNull
    Map<Key, SignatureVerificationFuture> verify(
            @NonNull Bytes signedBytes, @NonNull Set<ExpandedSignaturePair> sigPairs);
}
