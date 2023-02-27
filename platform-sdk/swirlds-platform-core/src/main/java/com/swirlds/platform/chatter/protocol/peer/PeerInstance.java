/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.peer;

import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;

/**
 * All instances related to a single chatter peer
 *
 * @param communicationState
 * 		the state of chatter communication with the peer
 * @param inputHandler
 * 		handles input messages
 * @param outputAggregator
 * 		provides messages to be sent to the peer
 * @param state
 * 		the state of this chatter connection
 */
public record PeerInstance(
        CommunicationState communicationState,
        PeerGossipState state,
        MessageProvider outputAggregator,
        PeerMessageHandler inputHandler) {}
