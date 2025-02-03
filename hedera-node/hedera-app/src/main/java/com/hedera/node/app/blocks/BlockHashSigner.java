/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the ability to asynchronously sign a block hash.
 */
public interface BlockHashSigner {
    /**
     * Whether the signer is ready.
     */
    boolean isReady();

    /**
     * Returns a future that resolves to the signature of the given block hash.
     *
     * @param blockHash the block hash
     * @return the future
     */
    CompletableFuture<Bytes> signFuture(@NonNull Bytes blockHash);
}
