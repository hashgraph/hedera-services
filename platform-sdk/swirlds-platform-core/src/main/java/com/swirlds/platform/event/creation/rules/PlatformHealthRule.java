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

package com.swirlds.platform.event.creation.rules;

import static com.swirlds.common.utility.CompareTo.isLessThanOrEqualTo;
import static com.swirlds.platform.event.creation.EventCreationStatus.OVERLOADED;

import com.swirlds.platform.event.creation.EventCreationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A rule that permits event creation only when the platform is considered to be in a healthy state.
 */
public class PlatformHealthRule implements EventCreationRule {

    private final Duration maximumPermissibleUnhealthyDuration;
    private final Supplier<Duration> currentUnhealthyDurationSupplier;

    /**
     * Constructor.
     *
     * @param maximumPermissibleUnhealthyDuration the maximum permissible duration that the platform can be unhealthy
     *                                            before this rule will prevent event creation
     * @param currentUnhealthyDurationSupplier    a supplier of the current unhealthy duration
     */
    public PlatformHealthRule(
            @NonNull final Duration maximumPermissibleUnhealthyDuration,
            @NonNull final Supplier<Duration> currentUnhealthyDurationSupplier) {

        this.maximumPermissibleUnhealthyDuration = Objects.requireNonNull(maximumPermissibleUnhealthyDuration);
        this.currentUnhealthyDurationSupplier = Objects.requireNonNull(currentUnhealthyDurationSupplier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        return isLessThanOrEqualTo(currentUnhealthyDurationSupplier.get(), maximumPermissibleUnhealthyDuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return OVERLOADED;
    }
}
