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

package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.block.stream.BlockProof;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.Nullable;

public record QueuedStateSignatureTransaction(
        long nodeId, @Nullable StateSignatureTransaction sig, @Nullable BlockProof proof) {
    // Ensure that the signature is not null
    public QueuedStateSignatureTransaction {
        if (sig == null && proof == null) {
            throw new IllegalArgumentException("StateSignatureTransaction and BlockProof cannot both be null");
        }
    }
}
