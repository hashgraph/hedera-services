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
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Factory for all stores. Besides providing helpers to create stores, this class also tracks, which
 * {@link WritableStates} have been used.
 */
public class WritableStoreFactory {
    private final HederaState hederaState;
    private final Map<String, WritableStates> usedWritableStates = new HashMap<>();

    /**
     * Constructor of {@link WritableStoreFactory}
     *
     * @param hederaState the {@link HederaState} that all stores are based upon
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    @Inject
    public WritableStoreFactory(@NonNull final HederaState hederaState) {
        this.hederaState = requireNonNull(hederaState);
    }

    /**
     * Get a {@link Map} of all {@link WritableStates} that have been used to construct stores. The
     * key of the {@code Map} is the key of the particular {@code ReadableStates}.
     *
     * @return a {@link Map} that contains all {@link WritableStates} that have been used
     */
    @NonNull
    public Map<String, WritableStates> getUsedWritableStates() {
        return usedWritableStates;
    }

    /**
     * Get a specific {@link WritableStates}. If the {@code WritableStates} does not exist yet, a
     * new one is created.
     *
     * @param key the {@code key} of the {@link WritableStates}
     * @return the {@link WritableStates}, either one that was created earlier or a new one
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    @NonNull
    public WritableStates getWritableStates(@NonNull final String key) {
        requireNonNull(key);
        return usedWritableStates.computeIfAbsent(key, hederaState::createWritableStates);
    }

    /**
     * Get a {@link WritableTopicStore}
     *
     * @return a new {@link WritableTopicStore}
     */
    @NonNull
    public WritableTopicStore getWritableTopicStore() {
        final var topicStates = getWritableStates(ConsensusService.NAME);
        return new WritableTopicStore(topicStates);
    }
}
