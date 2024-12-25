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

import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public class HintsOperationsImpl implements HintsOperations {
    @Override
    public Bytes computeHints(@NonNull final BlsPublicKey publicKey, final int n) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public boolean validateHints(@NonNull final BlsPublicKey publicKey, @NonNull final Bytes hints, final int n) {
        requireNonNull(publicKey);
        requireNonNull(hints);
        throw new AssertionError("Not implemented");
    }

    @Override
    public PreprocessedKeys aggregate(@NonNull Map<Long, HintsKey> hintKeys, @NonNull Map<Long, Long> weights, int n) {
        requireNonNull(hintKeys);
        requireNonNull(weights);
        throw new AssertionError("Not implemented");
    }
}
