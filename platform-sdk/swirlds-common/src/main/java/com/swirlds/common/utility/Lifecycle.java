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

package com.swirlds.common.utility;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object with a well-defined start/stop lifecycle.
 */
public interface Lifecycle extends Startable, Stoppable {

    /**
     * Get the current phase of this object.
     *
     * @return the object's current lifecycle phase
     */
    @NonNull
    LifecyclePhase getLifecyclePhase();

    /**
     * Throw an exception if the object is not in the expected phase.
     *
     * @param phase
     * 		the expected phase
     * @throws LifecycleException
     * 		if the object is not in the expected phase
     */
    default void throwIfNotInPhase(@NonNull final LifecyclePhase phase) {
        throwIfNotInPhase(phase, "object is in an unexpected phase");
    }

    /**
     * Throw an exception if the object is not in the expected phase.
     *
     * @param phase
     * 		the expected phase
     * @param errorMessage
     * 		a message that provides additional information if an exception is thrown
     * @throws LifecycleException
     * 		if the object is not in the expected phase
     */
    default void throwIfNotInPhase(@NonNull final LifecyclePhase phase, @NonNull final String errorMessage) {
        final LifecyclePhase currentPhase = getLifecyclePhase();
        if (currentPhase != phase) {
            throw new LifecycleException(
                    errorMessage + ": current phase = " + currentPhase + ", expected phase = " + phase);
        }
    }

    /**
     * Throw an exception if the object is in a phase after the specified phase.
     *
     * @param phase        the phase we should not be after
     * @throws LifecycleException if the object is after the specified phase
     */
    default void throwIfAfterPhase(@NonNull final LifecyclePhase phase) {
        throwIfAfterPhase(phase, "object is in an unexpected phase");
    }

    /**
     * Throw an exception if the object is in a phase after the specified phase.
     *
     * @param phase        the phase we should not be after
     * @param errorMessage a message that provides additional information if an exception is thrown
     * @throws LifecycleException if the object is after the specified phase
     */
    default void throwIfAfterPhase(@NonNull final LifecyclePhase phase, @NonNull final String errorMessage) {
        final LifecyclePhase currentPhase = getLifecyclePhase();
        if (currentPhase.ordinal() > phase.ordinal()) {
            throw new LifecycleException(
                    errorMessage + ": current phase = " + currentPhase + ", expected not to be after phase = " + phase);
        }
    }
}
