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

package com.swirlds.platform.stats.cycle;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Description of a cycle tracked by metrics
 */
public class CycleDefinition {
    private final String category;
    private final String name;
    private final List<String> intervalNames;
    private final List<String> intervalDesc;
    private final int numIntervals;

    /**
     * @param category
     * 		the category of this stat
     * @param name
     * 		a name that describes the cycle in its entirety
     * @param intervalNames
     * 		a list of names and describe each time interval. The size must match {@code intervalDescriptions}
     * @param intervalDescriptions
     * 		a list of descriptions for each time interval. The size must match {@code intervalNames}
     */
    public CycleDefinition(
            final String category,
            final String name,
            final List<String> intervalNames,
            final List<String> intervalDescriptions) {
        if (intervalNames.isEmpty()) {
            throw new IllegalArgumentException(("The number of intervals must be at least 1."));
        }
        if (intervalNames.size() != intervalDescriptions.size()) {
            throw new IllegalArgumentException(String.format(
                    "The number of descriptions for %s (%d) does not match the number of names (%d).",
                    name, intervalNames.size(), intervalDescriptions.size()));
        }

        this.category = category;
        this.name = name;
        this.intervalNames = intervalNames;
        this.intervalDesc = intervalDescriptions;
        this.numIntervals = intervalNames.size();
    }

    /**
     * @param category
     * 		the category of this stat
     * @param name
     * 		a name that describes the cycle in its entirety
     * @param namesAndDescriptions
     * 		a list of names and descriptions for each time interval
     */
    public CycleDefinition(
            final String category, final String name, final List<Pair<String, String>> namesAndDescriptions) {
        this(
                category,
                name,
                namesAndDescriptions.stream().map(Pair::getLeft).collect(Collectors.toList()),
                namesAndDescriptions.stream().map(Pair::getRight).collect(Collectors.toList()));
    }

    /**
     * @return the category of this metric cycle
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return the name used for all metrics in this cycle
     */
    public String getName() {
        return name;
    }

    /**
     * @param index
     * 		the index of the cycle interval, fist index is 0
     * @return name of this cycle interval
     */
    public String getIntervalName(final int index) {
        return intervalNames.get(index);
    }

    /**
     * @param index
     * 		the index of the cycle interval, fist index is 0
     * @return the full name of this cycle interval, including the cycle prefix
     */
    public String getDisplayName(final int index) {
        return name + "-" + getIntervalName(index);
    }

    /**
     * @param index
     * 		the index of the cycle interval, fist index is 0
     * @return description of this cycle interval
     */
    public String getIntervalDescription(final int index) {
        return intervalDesc.get(index);
    }

    /**
     * @return the number of intervals in this cycle
     */
    public int getNumIntervals() {
        return numIntervals;
    }
}
