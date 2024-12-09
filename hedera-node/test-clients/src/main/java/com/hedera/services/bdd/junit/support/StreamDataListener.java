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

package com.hedera.services.bdd.junit.support;

import com.hedera.hapi.block.stream.Block;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A listener that receives record stream items and transaction sidecar records.
 */
public interface StreamDataListener {
    /**
     * Called when a new block is received.
     * @param block the new block
     */
    default void onNewBlock(@NonNull final Block block) {}

    default void onNewItem(RecordStreamItem item) {}

    default void onNewSidecar(TransactionSidecarRecord sidecar) {}

    default String name() {
        return "AnonymousListener";
    }
}
