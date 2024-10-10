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

package com.swirlds.common.platform;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A cache for NodeId objects.
 *
 * It's useful to support code that uses long values to identify nodes (e.g. the Roster)
 * and needs to interact with code that uses NodeId objects for the same purpose
 * as it helps prevent creating many duplicate NodeId instances for the same long id value.
 */
final class NodeIdCache {
    private NodeIdCache() {}

    /** Minimum node id to cache. MUST BE non-negative. */
    private static final int MIN = 0;
    /** Maximum node id to cache. MUST BE non-negative, &gt;= MIN, and reasonably small. */
    private static final int MAX = 63;

    private static final NodeId[] CACHE = new NodeId[MAX - MIN + 1];

    static {
        for (int id = MIN; id <= MAX; id++) {
            CACHE[id - MIN] = new NodeId(id);
        }
    }

    /**
     * Fetch a NodeId value from the cache, or create a NodeId.of object.
     * The caller MUST NOT mutate the returned object even though the NodeId class is technically mutable.
     *
     * @param id a node id value
     * @return a NodeId object
     */
    @NonNull
    static NodeId getOrCreate(final long id) {
        if (id >= MIN && id <= MAX) {
            return CACHE[(int) id - MIN];
        }
        return new NodeId(id);
    }
}
