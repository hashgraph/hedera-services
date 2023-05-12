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

package com.swirlds.virtualmap.datasource;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.crypto.Hash;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Represents a virtual internal node. An internal node is essentially just a path, and a hash.
 */
public final class VirtualInternalRecord extends VirtualRecord {
    /**
     * Create a new {@link VirtualInternalRecord} with a null hash.
     *
     * @param path
     *		must be non-negative
     */
    public VirtualInternalRecord(long path) {
        super(path, null);
    }

    /**
     * Create a new {@link VirtualInternalRecord} with both a path and a hash.
     *
     * @param path
     *		Must be non-negative
     * @param hash
     *      The hash, which may be null.
     */
    public VirtualInternalRecord(long path, Hash hash) {
        super(path, hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("path", getPath())
                .append("hash", getHash())
                .toString();
    }
}
