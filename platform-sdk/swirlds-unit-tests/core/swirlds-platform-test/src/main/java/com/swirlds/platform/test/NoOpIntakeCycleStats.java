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

package com.swirlds.platform.test;

import static org.mockito.Mockito.mock;

import com.swirlds.base.time.TimeFactory;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.intake.IntakeCycleStats;

public class NoOpIntakeCycleStats extends IntakeCycleStats {
    public NoOpIntakeCycleStats() {
        super(TimeFactory.getOsTime(), mock(Metrics.class));
    }

    @Override
    public void startedIntake() {
        // no-op
    }

    @Override
    public void receivedUnlinkedEvent() {
        // no-op
    }

    @Override
    public void dispatchedReceived() {
        // no-op
    }

    @Override
    public void doneLinking() {
        // no-op
    }

    @Override
    public void doneIntake() {
        // no-op
    }

    @Override
    public void startIntakeAddEvent() {
        // no-op
    }

    @Override
    public void doneValidation() {
        // no-op
    }

    @Override
    public void dispatchedPreConsensus() {
        // no-op
    }

    @Override
    public void addedToConsensus() {
        // no-op
    }

    @Override
    public void dispatchedAdded() {
        // no-op
    }

    @Override
    public void dispatchedRound() {
        // no-op
    }

    @Override
    public void dispatchedStale() {
        // no-op
    }

    @Override
    public void doneIntakeAddEvent() {
        // no-op
    }
}
