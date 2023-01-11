/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A {@code StoreCache} caches stores for all active states. */
public class StoreCache {

    /**
     * Returns the {@link ReadableAccountStore} for the provided {@link HederaState}.
     *
     * @param state the {@code HederaState} that is the base for the {@code AccountStore}
     * @return the {@code AccountStore} for the provided state, either new or cached
     */
    @NonNull
    public ReadableAccountStore getAccountStore(@NonNull final HederaState state) {
        requireNonNull(state);

        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the {@link ReadableScheduleStore} for the provided {@link HederaState}.
     *
     * @param state the {@code HederaState} that is the base for the {@code ScheduleStore}
     * @return the {@code ScheduleStore} for the provided state, either new or cached
     */
    @NonNull
    public ReadableScheduleStore getScheduleStore(@NonNull final HederaState state) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the {@link ReadableTokenStore} for the provided {@link HederaState}.
     *
     * @param state the {@code HederaState} that is the base for the {@code TokenStore}
     * @return the {@code TokenStore} for the provided state, either new or cached
     */
    @NonNull
    public ReadableTokenStore getTokenStore(@NonNull final HederaState state) {
        throw new UnsupportedOperationException("not implemented");
    }
}
