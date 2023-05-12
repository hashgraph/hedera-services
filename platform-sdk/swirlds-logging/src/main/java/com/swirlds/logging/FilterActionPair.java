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

package com.swirlds.logging;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents an action that should be performed when an entry passes a filter.
 */
public class FilterActionPair<T> {

    private Predicate<T> filter;
    private Consumer<T> action;

    /**
     * Create a new pair.
     *
     * @param filter
     * 		the filter to be checked. If the predicate returns true then the entry is allowed
     * 		to pass, otherwise it is rejected. If null then all entries are considered to pass the filter.
     * @param action
     * 		the action to perform if an event passes the filter.
     */
    public FilterActionPair(Predicate<T> filter, Consumer<T> action) {
        this.filter = filter;
        this.action = action;
    }

    /**
     * Check if the entry passes the filter, and if it does then perform the configured action.
     *
     * @param entry
     * 		the entry that is being handled
     */
    public void handle(T entry) {
        if (filter == null || filter.test(entry)) {
            action.accept(entry);
        }
    }
}
