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

package com.swirlds.platform.dispatch.triggers.control;

import com.swirlds.platform.dispatch.types.TriggerThree;
import com.swirlds.platform.state.signed.StateToDiskReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Sends dispatches when a dump of a signed state is requested.
 */
@FunctionalInterface
public interface StateDumpRequestedTrigger extends TriggerThree<Long, StateToDiskReason, Boolean> {

    /**
     * Request that a signed state be dumped to disk.
     *
     * @param desiredRound the round that should be dumped if still in memory. If the desired round is not in memory
     *                     then the most recent immutable state will be dumped. If null, then the most recent state will
     *                     be dumped.
     * @param reason       reason why the state is being dumped
     * @param blocking     if this method should block until the operation has been completed
     */
    @Override
    void dispatch(
            @Nullable final Long desiredRound,
            @NonNull final StateToDiskReason reason,
            @NonNull final Boolean blocking);
}
