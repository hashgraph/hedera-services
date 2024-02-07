/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.merkle.test.fixtures.map.pta.MapKey;
import java.util.Objects;

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
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final ExpirationRecordEntry that = (ExpirationRecordEntry) other;
        return earliestExpiry == that.earliestExpiry && Objects.equals(mapKey, that.mapKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(earliestExpiry, mapKey);
    }

    @Override
    public String toString() {
        return String.format("%s: id: %s; earliestExpiry: %d", "ExpirationRecordEntry", mapKey, earliestExpiry);
    }
}
