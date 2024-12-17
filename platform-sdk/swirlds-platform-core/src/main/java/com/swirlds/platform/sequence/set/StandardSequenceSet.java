/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sequence.set;

import com.swirlds.platform.sequence.map.SequenceMap;
import com.swirlds.platform.sequence.map.StandardSequenceMap;
import com.swirlds.platform.sequence.set.internal.AbstractSequenceSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * A lock free {@link SequenceSet}.
 *
 * @param <T> the type of the element contained within this set
 */
public class StandardSequenceSet<T> extends AbstractSequenceSet<T> {

    /**
     * Create a new lock free {@link SequenceSet} that does not permit expansion.
     *
     * @param lowestAllowedSequenceNumber the initial lowest permitted sequence in the set
     * @param sequenceNumberCapacity      the number of sequence numbers permitted to exist in this data structure. E.g.
     *                                    if the lowest allowed sequence number is 100 and the capacity is 10, then
     *                                    values with a sequence number between 100 and 109 (inclusive) will be allowed,
     *                                    and any value with a sequence number outside that range will be rejected.
     * @param getSequenceNumberFromEntry  given an entry, extract the sequence number
     */
    public StandardSequenceSet(
            final long lowestAllowedSequenceNumber,
            final int sequenceNumberCapacity,
            @NonNull final ToLongFunction<T> getSequenceNumberFromEntry) {

        super(lowestAllowedSequenceNumber, sequenceNumberCapacity, false, getSequenceNumberFromEntry);
    }

    /**
     * Create a new lock free {@link SequenceSet}.
     *
     * @param lowestAllowedSequenceNumber the initial lowest permitted sequence in the set
     * @param sequenceNumberCapacity      the number of sequence numbers permitted to exist in this data structure. E.g.
     *                                    if the lowest allowed sequence number is 100 and the capacity is 10, then
     *                                    values with a sequence number between 100 and 109 (inclusive) will be allowed,
     *                                    and any value with a sequence number outside that range will be rejected.
     * @param allowExpansion              if true, then instead of rejecting elements with a sequence number higher than
     *                                    the allowed by the current capacity, increase capacity and then insert the
     *                                    element. Does not expand if the sequence number is too low to fit in the
     *                                    current capacity.
     * @param getSequenceNumberFromEntry  given an entry, extract the sequence number
     */
    public StandardSequenceSet(
            final long lowestAllowedSequenceNumber,
            final int sequenceNumberCapacity,
            final boolean allowExpansion,
            @NonNull final ToLongFunction<T> getSequenceNumberFromEntry) {

        super(lowestAllowedSequenceNumber, sequenceNumberCapacity, allowExpansion, getSequenceNumberFromEntry);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected SequenceMap<T, Boolean> buildMap(
            final long lowestAllowedSequenceNumber,
            final int sequenceNumberCapacity,
            final boolean allowExpansion,
            @NonNull final ToLongFunction<T> getSequenceNumberFromEntry) {

        Objects.requireNonNull(getSequenceNumberFromEntry);

        return new StandardSequenceMap<>(
                lowestAllowedSequenceNumber, sequenceNumberCapacity, allowExpansion, getSequenceNumberFromEntry);
    }
}
