/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.types;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A simple long pair. This record is used to create a pair of longs that can be used as a range for
 * properties.
 */
public record LongPair(@NonNull Long left, @NonNull Long right) {

    /**
     * Creates a new {@link LongPair} instance.
     *
     * @param left the left value
     * @param right the right value
     * @throws NullPointerException if either left or right is null
     */
    public LongPair {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");
    }

    /**
     * Checks if the given value is within the range of this pair.
     *
     * @param value the value to check
     * @return true if the value is within the range, false otherwise
     */
    public boolean containsInclusive(final long value) {
        return left <= value && value <= right;
    }
}
