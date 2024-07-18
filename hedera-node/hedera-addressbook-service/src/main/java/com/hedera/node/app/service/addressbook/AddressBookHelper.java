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

package com.hedera.node.app.service.addressbook;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;

import com.hedera.hapi.node.state.common.EntityNumber;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class AddressBookHelper {
    public static final String NODES_KEY = "NODES";

    /**
     * Get the next Node ID number from the reaReadableNodeStore
     * @param nodeStore
     * @return nextNodeId
     */
    public static long getNextNodeID(@NonNull final ReadableNodeStore nodeStore) {
        requireNonNull(nodeStore);
        final long maxNodeId = StreamSupport.stream(
                        Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT), false)
                .mapToLong(EntityNumber::number)
                .max()
                .orElse(-1L);
        return maxNodeId + 1;
    }
}
