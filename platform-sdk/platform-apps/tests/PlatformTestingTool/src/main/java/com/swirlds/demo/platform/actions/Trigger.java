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

package com.swirlds.demo.platform.actions;

/**
 * A trigger for a given action that makes decisions based on the given node identifier and supplied state. This is
 * called by {@link TriggeredAction#check(Object, Object)} and it's variants to determine if conditions have been met by
 * the state instance.
 *
 * @param <N>
 * 		the type of the node identifier
 * @param <S>
 * 		the type of the state being tested
 */
@FunctionalInterface
public interface Trigger<N, S> {

    /**
     * Tests the supplied {@code state} originating from the specified {@code node} to determine if conditions have been
     * met that should trigger the associated {@link Action} to be fired.
     *
     * @param node
     * 		the node identifier of the network member who created the state
     * @param state
     * 		the state to be evaluated by this trigger
     * @return true if the conditions have been met and the associated {@link Action} should be fired; otherwise if
     * 		false is returned the associated {@link Action} will not be fired
     */
    boolean test(final N node, final S state);
}
