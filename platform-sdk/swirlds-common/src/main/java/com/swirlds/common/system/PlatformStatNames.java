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

package com.swirlds.common.system;

/**
 * A class that holds all the user readable names
 */
public abstract class PlatformStatNames {
    /** number of consensus transactions per second handled by SwirldState.handleConsensusRound() */
    public static final String TRANSACTIONS_HANDLED_PER_SECOND = "transH/sec";
    /** number of consensus events in queue waiting to be handled */
    public static final String CONSENSUS_QUEUE_SIZE = "consEvents";
    /** number of pre-consensus events in queue waiting to be handled */
    public static final String PRE_CONSENSUS_QUEUE_SIZE = "preConsEvents";
    /** the number of creators that have more than one tip at the start of each sync */
    public static final String MULTI_TIPS_PER_SYNC = "multiTips/sync";
    /** the number of tips per sync at the start of each sync */
    public static final String TIPS_PER_SYNC = "tips/sync";
    /** the average number of generations that should be expired but cannot yet due to reservations. */
    public static final String GENS_WAITING_FOR_EXPIRY = "gensWaitingForExpiry";
    /** the ratio of rejected sync to accepted syncs over time. */
    public static final String REJECTED_SYNC_RATIO = "rejectedSyncRatio";

    public static final String TRANS_SUBMIT_MICROS = "avgTransSubmitMicros";
}
