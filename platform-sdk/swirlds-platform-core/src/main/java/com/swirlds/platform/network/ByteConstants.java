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

package com.swirlds.platform.network;

public final class ByteConstants {
    /** periodically sent to the SyncListener to keep connections alive */
    public static final byte HEARTBEAT = 0x40 /* 64 */;
    /** a reply sent back when a heartbeat is received by the SyncListener */
    public static final byte HEARTBEAT_ACK = 0x41 /* 65 */;
    /** sent to request a sync */
    public static final byte COMM_SYNC_REQUEST = 0x42 /* 66 */;
    /** sent as a reply to COMM_SYNC_REQUEST when accepting an incoming sync request */
    public static final byte COMM_SYNC_ACK = 0x43 /* 67 */;
    /** sent as a reply to COMM_SYNC_REQUEST when rejecting an incoming sync request (because too busy) */
    public static final byte COMM_SYNC_NACK = 0x44 /* 68 */;
    /** sent at the end of a sync, to show it's done */
    public static final byte COMM_SYNC_DONE = 0x45 /* 69 */;
    /** optionally sent during event transfer phase to assure the peer the connection is still open */
    public static final byte COMM_SYNC_ONGOING = 0x46 /* 70 */;
    /** sent after a new socket connection is made */
    public static final byte COMM_CONNECT = 0x47 /* 71 */;
    /** sent before sending each event */
    public static final byte COMM_EVENT_NEXT = 0x48 /* 72 */;
    /** sent when event sending is aborted */
    public static final byte COMM_EVENT_ABORT = 0x49 /* 73 */;
    /** sent after all events have been sent for this sync */
    public static final byte COMM_EVENT_DONE = 0x4a /* 74 */;
    /** sent if a node wants to get the latest signed state */
    public static final byte COMM_STATE_REQUEST = 0x4c /* 76 */;
    /** sent as a reply to COMM_STATE_REQUEST when accepting to transfer the latest state */
    public static final byte COMM_STATE_ACK = 0x4d /* 77 */;
    /** sent as a reply to COMM_STATE_REQUEST when NOT accepting to transfer the latest state */
    public static final byte COMM_STATE_NACK = 0x4e /* 78 */;
    /**
     * Private constructor to never instantiate this class
     */
    private ByteConstants() {}
}
