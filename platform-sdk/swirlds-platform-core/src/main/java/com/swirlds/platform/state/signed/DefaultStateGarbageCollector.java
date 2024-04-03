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

package com.swirlds.platform.state.signed;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is responsible for the deletion of signed states. In case signed state deletion is expensive, we never
 * want to delete a signed state on the last thread that releases it.
 */
public class DefaultStateGarbageCollector implements StateGarbageCollector {

    private final List<SignedState> states = new LinkedList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerState(@NonNull final ReservedSignedState state) {
        try (state) {
            // Intentionally hold a java reference without a signed state reference count.
            // This is the only place in the codebase that is allowed to do this.
            states.add(state.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeat(@NonNull final Instant now) {
        // TODO add a metric for number of undeleted states and time to delete each state

        final Iterator<SignedState> iterator = states.iterator();
        while (iterator.hasNext()) {
            final SignedState signedState = iterator.next();
            if (signedState.isEligibleForDeletion()) {
                signedState.delete();
                iterator.remove();
            }
        }
    }
}
