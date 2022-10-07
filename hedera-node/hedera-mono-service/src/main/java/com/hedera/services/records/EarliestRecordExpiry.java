/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.services.utils.EntityIdUtils.readableId;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Objects;

/**
 * Provides a process object useful for tracking the time of the earliest-expiring record associated
 * to an {@link AccountID}.
 */
public class EarliestRecordExpiry implements Comparable<EarliestRecordExpiry> {
    private final long earliestExpiry;
    private final AccountID id;

    public EarliestRecordExpiry(final long earliestExpiry, final AccountID id) {
        this.earliestExpiry = earliestExpiry;
        this.id = id;
    }

    @Override
    public int compareTo(final EarliestRecordExpiry that) {
        return Long.compare(this.earliestExpiry, that.earliestExpiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(earliestExpiry, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !o.getClass().equals(EarliestRecordExpiry.class)) {
            return false;
        }
        final var that = (EarliestRecordExpiry) o;
        return Objects.equals(this.id, that.id) && (this.earliestExpiry == that.earliestExpiry);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(EarliestRecordExpiry.class)
                .add("id", readableId(id))
                .add("earliestExpiry", earliestExpiry)
                .toString();
    }
}
