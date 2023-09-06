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

package com.swirlds.common.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Basic configuration data record. This record contains all general config properties that can not be defined for a
 * specific subsystem. The record is based on the definition of config data objects as described in {@link ConfigData}.
 *
 * <p>
 * Do not add new settings to this record unless you have a very good reason. New settings should go
 * into config records with a prefix defined by a {@link ConfigData @ConfigData("prefix")} tag. Adding
 * settings to this record pollutes the top level namespace.
 *
 * @param verifyEventSigs
 * 		verify event signatures (rather than just trusting they are correct)?
 * @param showInternalStats
 * 		show the user all statistics, including those with category "internal"?
 * @param verboseStatistics
 * 		show expand statistics values, inlcude mean, min, max, stdDev
 * @param numConnections
 * 		number of connections maintained by each member (syncs happen on random connections from that set
 * @param logStack
 * 		when converting an exception to a string for logging, should it include the stack trace?
 * @param sleepHeartbeat
 * 		send a heartbeat byte on each comm channel to keep it open, every this many milliseconds
 * @param statsSkipSeconds
 * 		number of seconds that the "all" history window skips at the start
 * @param freezeSecondsAfterStartup
 * 		do not create events for this many seconds after the platform has started (0 or less to not freeze at startup)
 * @param loadKeysFromPfxFiles
 * 		When enabled, the platform will try to load node keys from .pfx files located in the {@link PathsConfig keysDirPath}. If even a
 * 		single key is missing, the platform will warn and exit. If disabled, the platform will generate keys
 * 		deterministically.
 * @param jvmPauseDetectorSleepMs
 * 		period of JVMPauseDetectorThread sleeping in the unit of milliseconds
 * @param jvmPauseReportMs
 * 		log an error when JVMPauseDetectorThread detect a pause greater than this many milliseconds
 * @param enablePingTrans
 * 		if set to true, send a transaction every {@code pingTransFreq} providing the ping in milliseconds from self to
 * 		all peers
 * @param pingTransFreq
 * 		if {@code enablePingTrans} is set to true, the frequency at which to send transactions containing the average
 * 		ping from self to all peers, in seconds
 * @param hangingThreadDuration
 *      the length of time a gossip thread is allowed to wait when it is asked to shutdown.
 *      If a gossip thread takes longer than this period to shut down, then an error message is written to the log.
 * @param emergencyRecoveryFileLoadDir
 *      The path to look for an emergency recovery file on node start. If a file is present in this directory at
 *      startup, emergency recovery will begin.
 * @param genesisFreezeTime
 *      If this node starts from genesis, this value is used as the freeze time. This feature is deprecated and
 *      planned for removal in a future platform version.
 */
@ConfigData
public record BasicConfig(
        @ConfigProperty(defaultValue = "true") boolean verifyEventSigs,
        @ConfigProperty(defaultValue = "true") boolean showInternalStats,
        @ConfigProperty(defaultValue = "false") boolean verboseStatistics,
        @ConfigProperty(defaultValue = "40") int numConnections,
        @ConfigProperty(defaultValue = "true") boolean logStack,
        @ConfigProperty(defaultValue = "500") int sleepHeartbeat,
        @ConfigProperty(defaultValue = "60") double statsSkipSeconds,
        @ConfigProperty(defaultValue = "10") int freezeSecondsAfterStartup,
        @ConfigProperty(defaultValue = "true") boolean loadKeysFromPfxFiles,
        @ConfigProperty(defaultValue = "1000") int jvmPauseDetectorSleepMs,
        @ConfigProperty(defaultValue = "1000") int jvmPauseReportMs,
        @ConfigProperty(defaultValue = "true") boolean enablePingTrans,
        @ConfigProperty(defaultValue = "1") long pingTransFreq,
        @ConfigProperty(defaultValue = "60s") Duration hangingThreadDuration,
        @ConfigProperty(defaultValue = "data/saved") String emergencyRecoveryFileLoadDir,
        @ConfigProperty(defaultValue = "0") long genesisFreezeTime) {}
