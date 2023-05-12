/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

/**
 * @deprecated This is being used for the old (and fragile) style of String.contains log parsing.
 * 		Do not use this for any new logs.
 */
@Deprecated
public abstract class PlatformLogMessages {
    @Deprecated
    public static final String START_RECONNECT = "start reconnect";

    @Deprecated
    public static final String CHANGED_TO_ACTIVE = "Platform status changed to: ACTIVE";

    @Deprecated
    public static final String FINISHED_RECONNECT = "finished reconnect";

    @Deprecated
    public static final String RECV_STATE_HASH_MISMATCH = "Hash from received signed state does not match";

    @Deprecated
    public static final String RECV_STATE_ERROR = "Error while receiving a SignedState";

    @Deprecated
    public static final String RECV_STATE_IO_EXCEPTION = "IOException while receiving a SignedState";

    @Deprecated
    public static final String CHANGED_TO_BEHIND = "Platform status changed to: BEHIND";

    @Deprecated
    public static final String FALL_BEHIND_DO_NOT_RECONNECT = "has fallen behind, will die";

    @Deprecated
    public static final String SYNC_STALE_COMPENSATION_SUCCESS = "Compensating for stale events during gossip";

    @Deprecated
    public static final String SYNC_STALE_COMPENSATION_FAILURE =
            "Failed to compensate for stale events during gossip" + " due to delta exceeding threshold";
}
