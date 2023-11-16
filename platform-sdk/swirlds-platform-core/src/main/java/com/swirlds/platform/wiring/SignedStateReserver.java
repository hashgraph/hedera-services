/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.transformers.AdvancedTransformation;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Manages reservations of a signed state when it needs to be passed to one or more consumers. Whenever a state is
 * passed to this instance, the assumption will be that it reserved for the reserver, and it is responsible for
 * releasing it. The state is passed to downstream consumers with the same assumption: each consumer will get a state
 * with a reservation for itself, and it is responsible for releasing it.
 */
public class SignedStateReserver implements AdvancedTransformation<ReservedSignedState, ReservedSignedState> {
    private final String name;

    public SignedStateReserver(final String name) {
        this.name = name;
    }

    @Nullable
    @Override
    public ReservedSignedState transform(@NonNull final ReservedSignedState reservedSignedState) {
        return reservedSignedState.getAndReserve(name);
    }

    @Override
    public void cleanup(@NonNull final ReservedSignedState reservedSignedState) {
        reservedSignedState.close();
    }
}
