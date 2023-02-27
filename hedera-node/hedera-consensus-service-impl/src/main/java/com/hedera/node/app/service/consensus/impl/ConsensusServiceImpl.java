/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandlerImpl;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandlerImpl;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandlerImpl;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandlerImpl;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandlerImpl;
import com.hedera.node.app.service.consensus.impl.serdes.EntityNumSerdes;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/**
 * Standard implementation of the {@link ConsensusService}.
 */
public final class ConsensusServiceImpl implements ConsensusService {

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    public static final String TOPICS_KEY = "TOPICS";

    private final ConsensusCreateTopicHandlerImpl consensusCreateTopicHandler;

    private final ConsensusDeleteTopicHandlerImpl consensusDeleteTopicHandler;

    private final ConsensusGetTopicInfoHandlerImpl consensusGetTopicInfoHandler;

    private final ConsensusSubmitMessageHandlerImpl consensusSubmitMessageHandler;

    private final ConsensusUpdateTopicHandlerImpl consensusUpdateTopicHandler;

    /**
     * Creates a new {@link ConsensusServiceImpl} instance.
     */
    public ConsensusServiceImpl() {
        this.consensusCreateTopicHandler = new ConsensusCreateTopicHandlerImpl();
        this.consensusDeleteTopicHandler = new ConsensusDeleteTopicHandlerImpl();
        this.consensusGetTopicInfoHandler = new ConsensusGetTopicInfoHandlerImpl();
        this.consensusSubmitMessageHandler = new ConsensusSubmitMessageHandlerImpl();
        this.consensusUpdateTopicHandler = new ConsensusUpdateTopicHandlerImpl();
    }

    /**
     * Returns the {@link ConsensusCreateTopicHandlerImpl} instance.
     *
     * @return the {@link ConsensusCreateTopicHandlerImpl} instance.
     */
    @NonNull
    public ConsensusCreateTopicHandlerImpl getConsensusCreateTopicHandler() {
        return consensusCreateTopicHandler;
    }

    /**
     * Returns the {@link ConsensusDeleteTopicHandlerImpl} instance.
     *
     * @return the {@link ConsensusDeleteTopicHandlerImpl} instance.
     */
    @NonNull
    public ConsensusDeleteTopicHandlerImpl getConsensusDeleteTopicHandler() {
        return consensusDeleteTopicHandler;
    }

    /**
     * Returns the {@link ConsensusGetTopicInfoHandlerImpl} instance.
     *
     * @return the {@link ConsensusGetTopicInfoHandlerImpl} instance.
     */
    @NonNull
    public ConsensusGetTopicInfoHandlerImpl getConsensusGetTopicInfoHandler() {
        return consensusGetTopicInfoHandler;
    }

    /**
     * Returns the {@link ConsensusSubmitMessageHandlerImpl} instance.
     *
     * @return the {@link ConsensusSubmitMessageHandlerImpl} instance.
     */
    @NonNull
    public ConsensusSubmitMessageHandlerImpl getConsensusSubmitMessageHandler() {
        return consensusSubmitMessageHandler;
    }

    /**
     * Returns the {@link ConsensusUpdateTopicHandlerImpl} instance.
     *
     * @return the {@link ConsensusUpdateTopicHandlerImpl} instance.
     */
    @NonNull
    public ConsensusUpdateTopicHandlerImpl getConsensusUpdateTopicHandler() {
        return consensusUpdateTopicHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandlers() {
        return Set.of(
                consensusCreateTopicHandler,
                consensusDeleteTopicHandler,
                consensusSubmitMessageHandler,
                consensusUpdateTopicHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandlers() {
        return Set.of(consensusGetTopicInfoHandler);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null").register(consensusSchema());
    }

    private Schema consensusSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(topicsDef());
            }
        };
    }

    private StateDefinition<EntityNum, MerkleTopic> topicsDef() {
        final var keySerdes = new EntityNumSerdes();
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForSelfSerializable(MerkleTopic.CURRENT_VERSION, MerkleTopic::new);
        return StateDefinition.inMemory(TOPICS_KEY, keySerdes, valueSerdes);
    }
}
