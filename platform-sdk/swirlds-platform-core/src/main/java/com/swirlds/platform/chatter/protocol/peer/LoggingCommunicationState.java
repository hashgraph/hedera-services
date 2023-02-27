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

package com.swirlds.platform.chatter.protocol.peer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

/**
 * Used for debugging state transitions
 */
public class LoggingCommunicationState extends CommunicationState {
    private static final Logger logger = LogManager.getLogger(LoggingCommunicationState.class);
    private final long peerId;
    private final Marker marker;

    public LoggingCommunicationState(final long peerId, final Marker marker) {
        this.peerId = peerId;
        this.marker = marker;
    }

    private void log(final String method) {
        logger.info(marker, "'{}' peer: {}, new states:{} {}", method, peerId, syncState, commState);
    }

    @Override
    public void chatterSyncStarted() {
        super.chatterSyncStarted();
        log("chatterSyncStarted");
    }

    @Override
    public void chatterSyncStartingPhase3() {
        super.chatterSyncStartingPhase3();
        log("chatterSyncStartingPhase3");
    }

    @Override
    public void chatterSyncSucceeded() {
        super.chatterSyncSucceeded();
        log("chatterSyncSucceeded");
    }

    @Override
    public void chatterSyncFailed() {
        super.chatterSyncFailed();
        log("chatterSyncFailed");
    }

    @Override
    public void chatterStarted() {
        super.chatterStarted();
        log("chatterStarted");
    }

    @Override
    public void chatterEnded() {
        super.chatterEnded();
        log("chatterEnded");
    }

    @Override
    public void receivedEnd() {
        super.receivedEnd();
        log("receivedEnd");
    }

    @Override
    public void queueOverFlow() {
        super.queueOverFlow();
        log("queueOverFlow");
    }

    @Override
    public void suspend() {
        super.suspend();
        log("suspend");
    }

    @Override
    public void unsuspend() {
        super.unsuspend();
        log("unsuspend");
    }

    @Override
    public void reset() {
        super.reset();
        log("reset");
    }
}
