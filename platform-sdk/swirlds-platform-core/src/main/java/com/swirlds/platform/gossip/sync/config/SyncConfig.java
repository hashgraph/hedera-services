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

package com.swirlds.platform.gossip.sync.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration of the sync gossip algorithm
 *
 * @param syncAsProtocolEnabled
 *      if true, perform the sync gossip algorithm as a negotiated protocol using bidirectional connections.
 * @param syncSleepAfterFailedNegotiation
 *      the number of milliseconds to sleep after a failed negotiation when running the sync-as-a-protocol algorithm
 * @param syncProtocolPermitCount
 *      the number of permits to use when running the sync-as-a-protocol algorithm
 * @param syncProtocolHeartbeatPeriod
 *      the period at which the heartbeat protocol runs when the sync-as-a-protocol algorithm is active (milliseconds)
 * @param maxOutgoingSyncs
 * 		maximum number of simultaneous outgoing syncs initiated by me
 * @param maxIncomingSyncsInc
 * 		maximum number of simultaneous incoming syncs initiated by others, minus maxOutgoingSyncs. If there is a moment
 * 		where each member has maxOutgoingSyncs outgoing syncs in progress, then a fraction of at least:
 * 		(1 / (maxOutgoingSyncs + maxIncomingSyncsInc)) members will be willing to accept another incoming sync. So
 * 		even in the worst case, it should be possible to find a partner to sync with in about (maxOutgoingSyncs +
 * 		maxIncomingSyncsInc) tries, on average.
 * @param callerSkipsBeforeSleep
 * 		sleep sleepCallerSkips ms after the caller fails this many times to call a random member
 * @param sleepCallerSkips
 * 		caller sleeps this many milliseconds if it failed to connect to callerSkipsBeforeSleep in a row *
 */
@ConfigData("sync")
public record SyncConfig(
        @ConfigProperty(defaultValue = "true") boolean syncAsProtocolEnabled,
        @ConfigProperty(defaultValue = "25") int syncSleepAfterFailedNegotiation,
        @ConfigProperty(defaultValue = "17") int syncProtocolPermitCount,
        @ConfigProperty(defaultValue = "1000") int syncProtocolHeartbeatPeriod,
        @ConfigProperty(defaultValue = "2") int maxOutgoingSyncs,
        @ConfigProperty(defaultValue = "1") int maxIncomingSyncsInc,
        @ConfigProperty(defaultValue = "30") long callerSkipsBeforeSleep,
        @ConfigProperty(defaultValue = "50") long sleepCallerSkips) {}
