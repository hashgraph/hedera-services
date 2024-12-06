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

package com.hedera.node.app.service.schedule;

import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import java.util.Iterator;

/**
 * An iterator over executable transactions that can also purge state up to the next known executable transaction.
 */
public interface ExecutableTxnIterator extends Iterator<ExecutableTxn<? extends StreamBuilder>> {
    /**
     * Purges any expired state up to the point of the next known executable transaction.
     * @return whether any state was purged
     * @throws IllegalStateException if {@link Iterator#hasNext()} was never called
     */
    boolean purgeUntilNext();
}
