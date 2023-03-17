/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Definitions of all log markers.
 */
public enum LogMarker {

    /**
     * log all exceptions, and serious problems. These should never happen there is a bug in the code. In most cases,
     * this should include a full stack trace of the exception.
     */
    EXCEPTION(LogMarkerType.ERROR),

    /**
     * exceptions that shouldn't happen during testing, but can happen in production if there is a malicious
     * node. This should be turned off in production so that a malicious node cannot clutter the logs
     */
    TESTING_EXCEPTIONS(LogMarkerType.ERROR),

    /**
     * log the 4 sync exceptions (EOFException, SocketTimeoutException, SocketException, IOException)
     */
    SOCKET_EXCEPTIONS(LogMarkerType.ERROR),

    /**
     * log socket exceptions related to connecting to a node, this is a separate marker to avoid filling the log file
     * in case one node is down, or it has not started yet
     */
    TCP_CONNECT_EXCEPTIONS(LogMarkerType.ERROR),

    /**
     * exceptions that shouldn't happen during testing, except for all nodes in reconnect test
     */
    TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT(LogMarkerType.ERROR),

    /**
     * log errors.
     */
    ERROR(LogMarkerType.ERROR),

    /**
     * log any events received which were not valid
     */
    INVALID_EVENT_ERROR(LogMarkerType.ERROR),

    /**
     * to distinguish JVM pauses from other issues
     */
    JVM_PAUSE_WARN(LogMarkerType.WARNING),

    /**
     * logs events related to the startup of the application
     */
    STARTUP(LogMarkerType.INFO),

    /**
     * log all the steps of a sync
     */
    SYNC(LogMarkerType.INFO),

    /**
     * about to sync
     */
    SYNC_START(LogMarkerType.INFO),

    /**
     * Relevant information about each step of the sync without too much logging
     */
    SYNC_INFO(LogMarkerType.INFO),

    /**
     * All network related
     */
    NETWORK(LogMarkerType.INFO),

    /**
     * log each new event created (not received)
     */
    CREATE_EVENT(LogMarkerType.INFO),

    /**
     * log each event as it's added to the hashgraph
     */
    ADD_EVENT(LogMarkerType.INFO),

    /**
     * Marker for config
     */
    CONFIG(LogMarkerType.INFO),

    /**
     * log each event as it's added to the intake queue
     */
    INTAKE_EVENT(LogMarkerType.INFO),

    /** logs which throttles are preventing event creation */
    EVENT_CREATION_THROTTLE(LogMarkerType.INFO),

    /**
     * log when threads are being stopped and/or joined
     */
    THREADS(LogMarkerType.INFO),

    /**
     * log the sending and receiving of the heartbeats from SyncHeartbeat to SyncListener
     */
    HEARTBEAT(LogMarkerType.INFO),

    /**
     * logs info related to event signatures
     */
    EVENT_SIG(LogMarkerType.INFO),

    /**
     * logs the certificates either loaded of created
     */
    CERTIFICATES(LogMarkerType.INFO),

    /**
     * logs events related to event streaming
     */
    EVENT_STREAM(LogMarkerType.INFO),

    OBJECT_STREAM(LogMarkerType.INFO),

    OBJECT_STREAM_FILE(LogMarkerType.INFO),

    /**
     * logs events related platform freezing
     */
    FREEZE(LogMarkerType.INFO),

    /**
     * logs info related to signed states being saved to disk
     */
    STATE_TO_DISK(LogMarkerType.INFO),

    /**
     * logs info related to the signed state's hash
     */
    STATE_HASH(LogMarkerType.INFO),

    /**
     * log file signing events.
     */
    FILE_SIGN(LogMarkerType.INFO),

    /**
     * logs related to a signed state
     */
    SIGNED_STATE(LogMarkerType.INFO),

    /**
     * logs related to state recovery
     */
    EVENT_PARSER(LogMarkerType.INFO),

    /**
     * logs events related to reconnect
     */
    RECONNECT(LogMarkerType.INFO),

    /**
     * logs related to PTA runs. It is useful during debugging PTA with info from the platform
     */
    DEMO_INFO(LogMarkerType.INFO),

    /**
     * logs related to stale events
     */
    STALE_EVENTS(LogMarkerType.INFO),

    // Crypto
    ADV_CRYPTO_SYSTEM(LogMarkerType.INFO),

    /**
     * log all platform status changes
     */
    PLATFORM_STATUS(LogMarkerType.INFO),

    /**
     * Detail information about JasperDb.
     */
    JASPER_DB(LogMarkerType.INFO),

    /**
     * Detail information about MerkleDb.
     */
    MERKLE_DB(LogMarkerType.INFO),

    /**
     * Logs stats related to Virtual Merkle (nodes, map, etc).
     */
    VIRTUAL_MERKLE_STATS(LogMarkerType.INFO),

    /** logs related to network protocol negotiation */
    PROTOCOL_NEGOTIATION(LogMarkerType.INFO),

    /**
     * logs related to port forwarding
     */
    PORT_FORWARDING(LogMarkerType.INFO);

    private final LogMarkerType type;
    private final Marker marker;

    LogMarker(final LogMarkerType type) {
        this.type = type;
        this.marker = MarkerManager.getMarker(name());
    }

    /**
     * @return the com.swirlds.logging.LogMarkerType type instance referenced by this instance
     */
    public LogMarkerType getType() {
        return type;
    }

    /**
     * @return the org.apache.logging.log4j.Marker type instance referenced by this instance
     */
    public Marker getMarker() {
        return marker;
    }
}
