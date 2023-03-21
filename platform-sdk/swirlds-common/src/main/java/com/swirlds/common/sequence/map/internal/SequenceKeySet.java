/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.sequence.map.internal;

import static org.apache.commons.lang3.builder.ToStringStyle.JSON_STYLE;

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A set of keys with a particular sequence number. This object is designed to be reused as the allowable
 * window of sequence numbers shifts.
 *
 * @param <K>
 * 		the type of the key
 */
public class SequenceKeySet<K> {

    private long sequenceNumber;
    private final Set<K> keys = new HashSet<>();

    /**
     * Create an object capable of holding keys for a sequence number.
     *
     * @param sequenceNumber
     * 		the initial sequence number to be stored in this object
     */
    public SequenceKeySet(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Get the sequence number currently stored in this object.
     *
     * @return the current sequence number
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Set the sequence number currently stored in this object.
     *
     * @param sequenceNumber
     * 		the sequence number stored in this object
     */
    public void setSequenceNumber(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Get the set of keys contained by this object.
     *
     * @return a set of keys
     */
    public Set<K> getKeys() {
        return keys;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, JSON_STYLE)
                .append("sequence number", sequenceNumber)
                .append("size", keys.size())
                .toString();
    }
}
