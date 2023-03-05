/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import com.swirlds.common.utility.AutoCloseableWrapper;

/**
 * Provides access to recent copies of the state.
 */
public interface StateAccessor {

    /**
     * Get the most recent immutable state. This state may or may not be hashed when it is returned. Wrapper must be
     * closed when use of the state is no longer needed else resources may be leaked.
     *
     * @param <T> the type of the state
     * @return a wrapper around the most recent immutable state
     */
    <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState();

    /**
     * Get the most recent fully signed state. May return a wrapper around null if the platform does not have any fully
     * signed states still in memory (e.g. right after boot or if there is trouble with the collection of state
     * signatures).
     *
     * @param <T> the type of the state
     * @return a wrapper around the most recent fully signed state, or a wrapper around null if there are no available
     * fully signed states
     */
    <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState();
}
