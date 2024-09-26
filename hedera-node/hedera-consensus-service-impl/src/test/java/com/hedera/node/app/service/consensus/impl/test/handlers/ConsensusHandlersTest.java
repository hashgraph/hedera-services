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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusApproveAllowanceHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusHandlers;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusHandlersTest {
    private ConsensusCreateTopicHandler consensusCreateTopicHandler;
    private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;
    private ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler;
    private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;
    private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;
    private ConsensusApproveAllowanceHandler consensusApproveAllowanceHandler;

    private ConsensusHandlers consensusHandlers;

    @BeforeEach
    public void setUp() {
        consensusCreateTopicHandler = mock(ConsensusCreateTopicHandler.class);
        consensusDeleteTopicHandler = mock(ConsensusDeleteTopicHandler.class);
        consensusGetTopicInfoHandler = mock(ConsensusGetTopicInfoHandler.class);
        consensusSubmitMessageHandler = mock(ConsensusSubmitMessageHandler.class);
        consensusUpdateTopicHandler = mock(ConsensusUpdateTopicHandler.class);
        consensusApproveAllowanceHandler = mock(ConsensusApproveAllowanceHandler.class);

        consensusHandlers = new ConsensusHandlers(
                consensusCreateTopicHandler,
                consensusDeleteTopicHandler,
                consensusGetTopicInfoHandler,
                consensusSubmitMessageHandler,
                consensusUpdateTopicHandler,
                consensusApproveAllowanceHandler);
    }

    @Test
    void consensusCreateTopicHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusCreateTopicHandler,
                consensusHandlers.consensusCreateTopicHandler(),
                "consensusCreateTopicHandler does not return correct instance");
    }

    @Test
    void consensusDeleteTopicHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusDeleteTopicHandler,
                consensusHandlers.consensusDeleteTopicHandler(),
                "consensusDeleteTopicHandler does not return correct instance");
    }

    @Test
    void consensusGetTopicInfoHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusGetTopicInfoHandler,
                consensusHandlers.consensusGetTopicInfoHandler(),
                "consensusGetTopicInfoHandler does not return correct instance");
    }

    @Test
    void consensusSubmitMessageHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusSubmitMessageHandler,
                consensusHandlers.consensusSubmitMessageHandler(),
                "consensusSubmitMessageHandler does not return correct instance");
    }

    @Test
    void consensusUpdateTopicHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusUpdateTopicHandler,
                consensusHandlers.consensusUpdateTopicHandler(),
                "consensusUpdateTopicHandler does not return correct instance");
    }

    @Test
    void consensusApproveAllowanceHandlerReturnsCorrectInstance() {
        assertEquals(
                consensusApproveAllowanceHandler,
                consensusHandlers.consensusApproveAllowanceHandler(),
                "consensusApproveAllowanceHandler does not return correct instance");
    }
}
