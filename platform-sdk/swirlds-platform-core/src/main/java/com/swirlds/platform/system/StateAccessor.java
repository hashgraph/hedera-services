/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides access to recent copies of the state.
 */
public interface StateAccessor {

    /**
     * Get the most recent immutable state. This state may or may not be hashed when it is returned. Wrapper must be
     * closed when use of the state is no longer needed else resources may be leaked.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @param <T>    the type of the state
     * @return a wrapper around the most recent immutable state
     */
    <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull final String reason);
}
