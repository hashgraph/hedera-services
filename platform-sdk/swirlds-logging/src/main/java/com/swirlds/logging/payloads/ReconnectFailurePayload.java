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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when a reconnect attempt fails.
 */
public class ReconnectFailurePayload extends AbstractLogPayload {

    public enum CauseOfFailure {
        /**
         * Reconnect failed due to a socket exception.
         */
        SOCKET,
        /**
         * Reconnect failed due to the requested teacher being unwilling.
         */
        REJECTION,
        /**
         * Reconnect failed due to an error.
         */
        ERROR
    }

    private CauseOfFailure causeOfFailure;

    public ReconnectFailurePayload() {}

    /**
     * @param message
     * 		a human readable message
     * @param causeOfFailure
     * 		the reason why the reconnect failed
     */
    public ReconnectFailurePayload(final String message, final CauseOfFailure causeOfFailure) {
        super(message);
        this.causeOfFailure = causeOfFailure;
    }

    public CauseOfFailure getCauseOfFailure() {
        return causeOfFailure;
    }

    public void setCauseOfFailure(final CauseOfFailure causeOfFailure) {
        this.causeOfFailure = causeOfFailure;
    }
}
