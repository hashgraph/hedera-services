/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.hedera.service.gossip.impl.shadowgraph;

/**
 * An exception thrown by {Shadowgraph} when an event cannot be added to the shadow graph.
 */
public class ShadowgraphInsertionException extends RuntimeException {

    private final InsertableStatus status;

    /**
     * Constructs a new runtime exception with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause}.
     *
     * @param message
     * 		the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param status
     * 		the status of the event insertion
     */
    public ShadowgraphInsertionException(final String message, final InsertableStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * The status of the event which prevented its insertion into the shadow graph.
     *
     * @return the status
     */
    public InsertableStatus getStatus() {
        return status;
    }
}
