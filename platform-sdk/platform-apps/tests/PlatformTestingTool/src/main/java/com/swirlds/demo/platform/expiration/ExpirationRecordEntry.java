/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.expiration;

import com.swirlds.merkle.map.test.pta.MapKey;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Provides a process object useful for tracking the time of the
 * earliest-expiring record associated to an {@link MapKey}.
 */
public class ExpirationRecordEntry implements Comparable<ExpirationRecordEntry> {
    /**
     * earliest expiration time in a FCQ related to this MapKey
     */
    private final long earliestExpiry;

    private final MapKey mapKey;

    public ExpirationRecordEntry(long earliestExpiry, MapKey mapKey) {
        this.earliestExpiry = earliestExpiry;
        this.mapKey = mapKey;
    }

    public long getEarliestExpiry() {
        return earliestExpiry;
    }

    public MapKey getMapKey() {
        return mapKey;
    }

    @Override
    public int compareTo(ExpirationRecordEntry that) {
        return Long.compare(this.earliestExpiry, that.earliestExpiry);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpirationRecordEntry)) {
            return false;
        }
        ExpirationRecordEntry that = (ExpirationRecordEntry) o;
        return new EqualsBuilder()
                .append(this.mapKey, that.mapKey)
                .append(this.earliestExpiry, that.earliestExpiry)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(mapKey).append(earliestExpiry).toHashCode();
    }

    @Override
    public String toString() {
        return String.format("%s: id: %s; earliestExpiry: %d", "ExpirationRecordEntry", mapKey, earliestExpiry);
    }
}
