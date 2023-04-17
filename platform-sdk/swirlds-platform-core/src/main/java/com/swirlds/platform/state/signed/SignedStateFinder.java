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

package com.swirlds.platform.state.signed;

import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * Describes an object capable of searching for a state.
 */
@FunctionalInterface
public interface SignedStateFinder {

    /**
     * Search for a state matching the given criteria, and return the most recent state that matches. States are
     * iterated over from newest to oldest, starting at the latest immutable state.
     *
     * @param criteria the first state that matches this criteria is returned
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return a wrapper around the first matching state, or a wrapper around null if no state currently in memory
     * matches the criteria
     */
    @NonNull
    ReservedSignedState find(@NonNull final Predicate<SignedState> criteria, @NonNull final String reason);
}
