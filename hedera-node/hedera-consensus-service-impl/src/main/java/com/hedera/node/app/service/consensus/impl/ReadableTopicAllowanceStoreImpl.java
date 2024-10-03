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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPIC_ALLOWANCES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TopicAllowanceId;
import com.hedera.hapi.node.base.TopicAllowanceValue;
import com.hedera.node.app.service.consensus.ReadableTopicAllowanceStore;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class ReadableTopicAllowanceStoreImpl implements ReadableTopicAllowanceStore {
    /**
     * The underlying data storage class that holds the Topic Allowances data.
     */
    private final ReadableKVState<TopicAllowanceId, TopicAllowanceValue> readableState;

    /**
     * Create a new {@link ReadableTopicAllowanceStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableTopicAllowanceStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.readableState = states.get(TOPIC_ALLOWANCES_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public TopicAllowanceValue get(@NonNull final TopicAllowanceId allowanceId) {
        requireNonNull(allowanceId);
        return readableState.get(allowanceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@NonNull final TopicAllowanceId allowanceId) {
        requireNonNull(allowanceId);
        return readableState.contains(allowanceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long sizeOfState() {
        return readableState.size();
    }
}
