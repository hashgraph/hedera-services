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
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ConsensusService} {@link Service}.
 */
public final class ConsensusServiceImpl implements ConsensusService {

    private final ConsensusCreateTopicHandler consensusCreateTopicHandler;

    private final ConsensusDeleteTopicHandler consensusDeleteTopicHandler;

    private final ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler;

    private final ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    private final ConsensusUpdateTopicHandler consensusUpdateTopicHandler;

    /**
     * Creates a new {@link ConsensusServiceImpl} instance.
     */
    public ConsensusServiceImpl() {
        this.consensusCreateTopicHandler = new ConsensusCreateTopicHandler();
        this.consensusDeleteTopicHandler = new ConsensusDeleteTopicHandler();
        this.consensusGetTopicInfoHandler = new ConsensusGetTopicInfoHandler();
        this.consensusSubmitMessageHandler = new ConsensusSubmitMessageHandler();
        this.consensusUpdateTopicHandler = new ConsensusUpdateTopicHandler();
    }

    /**
     * Returns the {@link ConsensusCreateTopicHandler} instance.
     *
     * @return the {@link ConsensusCreateTopicHandler} instance.
     */
    @NonNull
    public ConsensusCreateTopicHandler getConsensusCreateTopicHandler() {
        return consensusCreateTopicHandler;
    }

    /**
     * Returns the {@link ConsensusDeleteTopicHandler} instance.
     *
     * @return the {@link ConsensusDeleteTopicHandler} instance.
     */
    @NonNull
    public ConsensusDeleteTopicHandler getConsensusDeleteTopicHandler() {
        return consensusDeleteTopicHandler;
    }

    /**
     * Returns the {@link ConsensusGetTopicInfoHandler} instance.
     *
     * @return the {@link ConsensusGetTopicInfoHandler} instance.
     */
    @NonNull
    public ConsensusGetTopicInfoHandler getConsensusGetTopicInfoHandler() {
        return consensusGetTopicInfoHandler;
    }

    /**
     * Returns the {@link ConsensusSubmitMessageHandler} instance.
     *
     * @return the {@link ConsensusSubmitMessageHandler} instance.
     */
    @NonNull
    public ConsensusSubmitMessageHandler getConsensusSubmitMessageHandler() {
        return consensusSubmitMessageHandler;
    }

    /**
     * Returns the {@link ConsensusUpdateTopicHandler} instance.
     *
     * @return the {@link ConsensusUpdateTopicHandler} instance.
     */
    @NonNull
    public ConsensusUpdateTopicHandler getConsensusUpdateTopicHandler() {
        return consensusUpdateTopicHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(
                consensusCreateTopicHandler,
                consensusDeleteTopicHandler,
                consensusSubmitMessageHandler,
                consensusUpdateTopicHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(consensusGetTopicInfoHandler);
    }
}
