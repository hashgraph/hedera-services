/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregator for group elements
 * <p>
 * Exists to take advantage of batch operations in the underlying group
 */
public class GroupElementAggregator {
    private final List<GroupElement> elements;

    /**
     * Constructor
     */
    public GroupElementAggregator() {
        this.elements = new ArrayList<>();
    }

    /**
     * Add an element to the aggregator
     *
     * @param element the element to add
     */
    public void add(@NonNull final GroupElement element) {
        elements.add(element);
    }

    /**
     * Compute the aggregate of the elements
     *
     * @return the aggregate of the elements
     */
    public GroupElement compute() {
        if (elements.isEmpty()) {
            throw new IllegalStateException("No elements to aggregate");
        }

        final GroupElement firstElement = elements.getFirst();
        for (final GroupElement element : elements) {
            if (!element.isSameGroup(firstElement)) {
                throw new IllegalArgumentException("All elements must be from the same group");
            }
        }

        return firstElement.getGroup().batchAdd(elements);
    }
}
