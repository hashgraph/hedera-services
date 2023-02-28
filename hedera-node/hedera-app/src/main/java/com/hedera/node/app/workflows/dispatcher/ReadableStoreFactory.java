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

package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for all readable stores.
 */
public class ReadableStoreFactory {

    private final HederaState state;

    public ReadableStoreFactory(@NonNull final HederaState state) {
        this.state = requireNonNull(state);
    }

    /**
     * Get a {@link ReadableAccountStore}
     *
     * @return a new {@link ReadableAccountStore}
     */
    @NonNull
    public ReadableAccountStore createAccountStore() {
        final var tokenStates = state.createReadableStates(TokenService.NAME);
        return new ReadableAccountStore(tokenStates);
    }

    /**
     * Get a {@link ReadableTopicStore}
     *
     * @return a new {@link ReadableTopicStore}
     */
    @NonNull
    public ReadableTopicStore createTopicStore() {
        final var topicStates = state.createReadableStates(ConsensusService.NAME);
        return new ReadableTopicStore(topicStates);
    }

    /**
     * Get a {@link ReadableScheduleStore}
     *
     * @return a new {@link ReadableScheduleStore}
     */
    @NonNull
    public ReadableScheduleStore createScheduleStore() {
        final var scheduleStates = state.createReadableStates(ScheduleService.NAME);
        return new ReadableScheduleStore(scheduleStates);
    }

    /**
     * Get a {@link ReadableTokenStore}
     *
     * @return a new {@link ReadableTokenStore}
     */
    @NonNull
    public ReadableTokenStore createTokenStore() {
        final var tokenStates = state.createReadableStates(TokenService.NAME);
        return new ReadableTokenStore(tokenStates);
    }
}
