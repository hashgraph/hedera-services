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

package com.swirlds.platform.stats;

import com.swirlds.common.UniqueId;
import com.swirlds.common.metrics.StatEntry;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Used to construct different types types of StatEntry instances
 */
@SuppressWarnings("removal")
public class StatConstructor {
    /** A reserved value used when none is set */
    private static final int NO_VALUE = 0;

    /**
     * Used to create a StatEntry whose value is limited to a single enum. This stat displays the enums unique ID
     * instead of the name so that it can be tracked on a graph.
     *
     * @param name
     * 		the name of the stat
     * @param category
     * 		the category of the stat
     * @param enumValues
     * 		all possible values for the enum
     * @param enumSupplier
     * 		supplier of the current value
     * @param <T>
     * 		The Enum type to track
     * @return a StatEntry that tracks an enum value
     */
    public static <T extends Enum<T> & UniqueId> StatEntry.Config<Integer> createEnumStat(
            final String name, final String category, final T[] enumValues, final Supplier<T> enumSupplier) {
        // check if the reserved value is being used
        if (Arrays.stream(enumValues).anyMatch((v) -> v.getId() == NO_VALUE)) {
            throw new IllegalArgumentException("Unique ID must not be equal to " + NO_VALUE);
        }

        // add all enum values to the description
        final StringBuilder desc = new StringBuilder();
        desc.append(NO_VALUE).append('=').append("NO_VALUE").append(' ');
        Arrays.stream(enumValues)
                .forEach((v) ->
                        desc.append(v.getId()).append('=').append(v.name()).append(' '));

        final Supplier<Integer> statValueSupplier = () -> {
            T t = enumSupplier.get();
            if (t == null) {
                return NO_VALUE;
            }
            return t.getId();
        };

        return new StatEntry.Config<>(category, name, Integer.class, statValueSupplier)
                .withDescription(desc.toString())
                .withFormat("%d");
    }
}
