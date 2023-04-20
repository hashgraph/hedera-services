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
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Asynchronously verifies signatures on a transaction given a set of keys.
 */
public interface SignatureVerifier {
    /**
     * Asynchronously verifies that there exists in {@code sigPairs} a {@link SignaturePair} such that it both matches
     * the given {@code key} AND matches the {@code signedBytes}.
     * @param signedBytes The signed bytes to verify
     * @param sigPairs The list of {@link SignaturePair}s, at least one of which must have signed {@code signedBytes}
     *                 and have a prefix matching the given {@code key}
     * @param key The key that must have signed the bytes
     * @return A {@link Future} indicating whether the {code signedBytes} were signed by the {@code key}.
     */
    @NonNull
    Future<Boolean> verify(@NonNull Bytes signedBytes, @NonNull List<SignaturePair> sigPairs, @NonNull Key key);
}
