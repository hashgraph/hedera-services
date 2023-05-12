/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.sequence.map;

import com.swirlds.common.sequence.map.internal.AbstractSequenceMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * A lock free implementation of {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class StandardSequenceMap<K, V> extends AbstractSequenceMap<K, V> {

    private long firstSequenceNumberInWindow;

    /**
     * Construct a {@link SequenceMap}.
     *
     * @param firstSequenceNumberInWindow
     * 		the lowest allowed sequence number
     * @param sequenceNumberCapacity
     * 		the number of sequence numbers permitted to exist in this data structure. E.g. if
     * 		the lowest allowed sequence number is 100 and the capacity is 10, then values with
     * 		a sequence number between 100 and 109 (inclusive) will be allowed, and any value
     * 		with a sequence number outside that range will be rejected.
     * @param getSequenceNumberFromKey
     * 		a method that extracts the sequence number from a key
     */
    public StandardSequenceMap(
            final long firstSequenceNumberInWindow,
            final int sequenceNumberCapacity,
            final ToLongFunction<K> getSequenceNumberFromKey) {

        super(firstSequenceNumberInWindow, sequenceNumberCapacity, getSequenceNumberFromKey);
        this.firstSequenceNumberInWindow = firstSequenceNumberInWindow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFirstSequenceNumberInWindow() {
        return firstSequenceNumberInWindow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setFirstSequenceNumberInWindow(final long firstSequenceNumberInWindow) {
        this.firstSequenceNumberInWindow = firstSequenceNumberInWindow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<K, V> buildDataMap() {
        return new HashMap<>();
    }
}
