/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.datasource;

import com.swirlds.virtualmap.internal.Path;
import java.util.Objects;

/**
 * Base class for {@link VirtualInternalRecord} and {@link VirtualLeafRecord}. Records are
 * data stored in the {@link VirtualDataSource} and in the {@code VirtualNodeCache}.
 * These records are mutable. Since the cache maintains versions across rounds (copies), it is necessary to
 * create new copies of the VirtualRecord in each round in which it is mutated.
 *
 * The class is sealed, and can only be extended by {@link VirtualInternalRecord} and {@link VirtualLeafRecord}.
 */
public abstract sealed class VirtualRecord permits VirtualInternalRecord, VirtualLeafRecord {
    /**
     * The path for this record. The path can change over time as nodes are added or removed.
     */
    private volatile long path;

    /**
     * Create a new VirtualRecord.
     *
     * @param path
     * 		Must be non-negative, or {@link Path#INVALID_PATH}.
     */
    protected VirtualRecord(long path) {
        assert path == Path.INVALID_PATH || path >= 0;
        this.path = path;
    }

    /**
     * Gets the path for this node.
     *
     * @return the path. Will not be INVALID_PATH.
     */
    public final long getPath() {
        return path;
    }

    /**
     * Sets the path for this node.
     *
     * @param path
     * 		must be non-negative
     */
    public final void setPath(long path) {
        assert path >= 0;
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final VirtualRecord that)) {
            return false;
        }

        return path == that.path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
