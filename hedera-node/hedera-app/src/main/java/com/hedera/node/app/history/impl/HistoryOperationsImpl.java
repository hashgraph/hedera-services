/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.ProofRoster;
import com.hedera.node.app.history.HistoryOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * Default implementation of the {@link HistoryOperations}.
 */
public class HistoryOperationsImpl implements HistoryOperations {
    @Override
    public Bytes hashProofRoster(@NonNull final ProofRoster roster) {
        requireNonNull(roster);
        return Bytes.EMPTY;
    }

    @Override
    public Bytes signSchnorr(@NonNull final Bytes message, @NonNull final Bytes privateKey) {
        requireNonNull(message);
        requireNonNull(privateKey);
        return Bytes.EMPTY;
    }

    @NonNull
    @Override
    public Bytes proveTransition(
            @Nullable final Bytes sourceProof,
            @NonNull final ProofRoster sourceProofRoster,
            @NonNull final Bytes targetProofRosterHash,
            @NonNull final Bytes targetMetadata,
            @NonNull final Map<Long, Bytes> sourceSignatures) {
        requireNonNull(sourceProofRoster);
        requireNonNull(targetProofRosterHash);
        requireNonNull(targetMetadata);
        requireNonNull(sourceSignatures);

        return Bytes.EMPTY;
    }
}
