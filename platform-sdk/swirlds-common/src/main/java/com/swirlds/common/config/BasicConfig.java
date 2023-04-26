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
import java.nio.file.Path;

/**
 * Basic configuration data record. This record contains all general config properties that can not be defined for a
 * specific subsystem. The record is based on the definition of config data objects as described in {@link ConfigData}.
 *
 * @param verifyEventSigs
 * 		verify event signatures (rather than just trusting they are correct)?
 * @param numCryptoThreads
 * 		number of threads used to verify signatures and generate keys, in parallel
 * @param showInternalStats
 * 		show the user all statistics, including those with category "internal"?
 * @param verboseStatistics
 * 		show expand statistics values, inlcude mean, min, max, stdDev
 * @param requireStateLoad
 * 		if set to true, the platform will fail to start if it fails to load a state from disk
 * @param signedStateFreq
 * 		hash and sign a state every signedStateFreq rounds. 1 means that a state will be signed every round, 2 means
 * 		every other round, and so on. If the value is 0 or less, no states will be signed
 * @param maxEventQueueForCons
 * 		max events that can be put in the forCons queue (q2) in ConsensusRoundHandler (0 for infinity)
 * @param throttleTransactionQueueSize
 * 		Stop accepting new non-system transactions into the 4 transaction queues if any of them have more than this
 * 		many.
 * @param throttle7
 * 		should we slow down when not behind? One of N members is "falling behind" when it receives at least (N +
 * 		throttle7threshold) events during a sync.
 * @param throttle7threshold
 * 		"falling behind" if received at least N * throttle7threshold events in a sync. A good choice for this constant
 * 		might be 1+2*d if a fraction d of received events are duplicates.
 * @param throttle7extra
 * 		if a sync has neither party falling behind, increase the bytes sent by this fraction
 * @param throttle7maxBytes
 * 		the maximum number of slowdown bytes to be sent during a sync
 * @param numConnections
 * 		number of connections maintained by each member (syncs happen on random connections from that set
 * @param bufferSize
 * 		for BufferedInputStream and BufferedOutputStream for syncing
 * @param halfLife
 * 		half life of some of the various statistics (give half the weight to the last halfLife seconds)
 * @param logStack
 * 		when converting an exception to a string for logging, should it include the stack trace?
 * @param doUpnp
 * 		should this set up uPnP port forwarding on the router once every 60 seconds?
 * @param useLoopbackIp
 * 		should be set to true when using the internet simulator
 * @param tcpNoDelay
 * 		if true, then Nagel's algorithm is disabled, which helps latency, hurts bandwidth usage
 * @param timeoutSyncClientSocket
 * 		timeout when waiting for data
 * @param timeoutSyncClientConnect
 * 		timeout when establishing a connection
 * @param timeoutServerAcceptConnect
 * 		timeout when server is waiting for another member to create a connection
 * @param deadlockCheckPeriod
 * 		check for deadlocks every this many milliseconds (-1 for never)
 * @param sleepHeartbeat
 * 		send a heartbeat byte on each comm channel to keep it open, every this many milliseconds
 * @param delayShuffle
 * 		the working state (stateWork) resets to a copy of the consensus state (stateCons) (which is called a shuffle)
 * 		when its queue is empty and the two are equal, but never twice within this many milliseconds
 * @param callerSkipsBeforeSleep
 * 		sleep sleepCallerSkips ms after the caller fails this many times to call a random member
 * @param sleepCallerSkips
 * 		caller sleeps this many milliseconds if it failed to connect to callerSkipsBeforeSleep in a row
 * @param statsSkipSeconds
 * 		number of seconds that the "all" history window skips at the start
 * @param threadPrioritySync
 * 		priority for threads that sync (in SyncCaller, SyncListener, SyncServer)
 * @param threadPriorityNonSync
 * 		priority for threads that don't sync (all but SyncCaller, SyncListener,SyncServer)
 * @param maxAddressSizeAllowed
 * 		the maximum number of address allowed in a address book, the same as the maximum allowed network size
 * @param freezeSecondsAfterStartup
 * 		do not create events for this many seconds after the platform has started (0 or less to not freeze at startup)
 * @param loadKeysFromPfxFiles
 * 		When enabled, the platform will try to load node keys from .pfx files located in the keysDirPath. If even a
 * 		single key is missing, the platform will warn and exit. If disabled, the platform will generate keys
 * 		deterministically.
 * @param maxTransactionBytesPerEvent
 * 		the maximum number of bytes that a single event may contain not including the event headers if a single
 * 		transaction exceeds this limit then the event will contain the single transaction only
 * @param maxTransactionCountPerEvent
 * 		the maximum number of transactions that a single event may contain
 * @param emergencyStateFileName
 * 		The CSV file name of the emergency state recovery file
 * @param eventIntakeQueueSize
 * 		The size of the event intake queue,
 *        {@link com.swirlds.common.threading.framework.config.QueueThreadConfiguration#UNLIMITED_CAPACITY} for
 * 		unbounded. It is best that this queue is large, but not unbounded. Filling it up can cause sync threads to drop
 * 		TCP connections, but leaving it unbounded can cause out of memory errors, even with the
 *        {@link #eventIntakeQueueThrottleSize()}, because syncs that started before the throttle engages can grow the
 * 		queue to very large sizes on larger networks.
 * @param checkSignedStateFromDisk
 * 		If true, the platform will recalculate the hash of the signed state and check it against the written hash. It
 * 		will also verify that the signatures are valid.
 * @param randomEventProbability
 * 		The probability that after a sync, a node will create an event with a random other parent. The probability is
 * 		is 1 in X, where X is the value of randomEventProbability. A value of 0 means that a node will not create any
 * 		random events. This feature is used to get consensus on events with no descendants which are created by nodes
 * 		who go offline.
 * @param rescueChildlessInverseProbability
 * 		The probability that we will create a child for a childless event. The probability is 1 / X, where X is the
 * 		value of rescueChildlessInverseProbability. A value of 0 means that a node will not create any children for
 * 		childless events.
 * @param runPauseCheckTimer
 * 		Run a thread that checks if the JVM pauses for a long time
 * @param enableEventStreaming
 * 		enable stream event to server
 * @param eventStreamQueueCapacity
 * 		capacity of the blockingQueue from which we take events and write to EventStream files
 * @param eventsLogPeriod
 * 		period of generating eventStream file
 * @param eventsLogDir
 * 		eventStream files will be generated in this directory
 * @param threadDumpPeriodMs
 * 		period of generating thread dump file in the unit of milliseconds
 * @param threadDumpLogDir
 * 		thread dump files will be generated in this directory
 * @param jVMPauseDetectorSleepMs
 * 		period of JVMPauseDetectorThread sleeping in the unit of milliseconds
 * @param jVMPauseReportMs
 * 		log an error when JVMPauseDetectorThread detect a pause greater than this many milliseconds
 * @param enableStateRecovery
 * 		Setting for state recover
 * @param playbackStreamFileDirectory
 * 		directory where event stream files are stored
 * @param playbackEndTimeStamp
 * 		last time stamp (inclusive) to stop the playback, format is "2019-10-02T19:46:30.037063163Z"
 * @param gossipWithDifferentVersions
 * 		if set to false, the platform will refuse to gossip with a node which has a different version of either
 * 		platform or application
 * @param enablePingTrans
 * 		if set to true, send a transaction every {@code pingTransFreq} providing the ping in milliseconds from self to
 * 		all peers
 * @param pingTransFreq
 * 		if {@code enablePingTrans} is set to true, the frequency at which to send transactions containing the average
 * 		ping from self to all peers, in seconds
 * @param staleEventPreventionThreshold
 * 		A setting used to prevent a node from generating events that will probably become stale. This value is
 * 		multiplied by the address book size and compared to the number of events received in a sync. If (
 * 		numEventsReceived > staleEventPreventionThreshold * addressBookSize ) then we will not create an event for that
 * 		sync, to reduce the probability of creating an event that will become stale.
 * @param eventIntakeQueueThrottleSize
 * 		The value for the event intake queue at which the node should stop syncing
 * @param transactionMaxBytes
 * 		maximum number of bytes allowed in a transaction
 * @param useTLS
 * 		should TLS be turned on, rather than making all sockets unencrypted?
 * @param socketIpTos
 * 		The IP_TOS to set for a socket, from 0 to 255, or -1 to not set one. This number (if not -1) will be part of
 * 		every TCP/IP packet, and is normally ignored by internet routers, but it is possible to make routers change
 * 		their handling of packets based on this number, such as for providing different Quality of Service (QoS). <a
 * 		href="https://en.wikipedia.org/wiki/Type_of_service">Type of Service</a>
 * @param maxIncomingSyncsInc
 * 		maximum number of simultaneous incoming syncs initiated by others, minus maxOutgoingSyncs. If there is a moment
 * 		where each member has maxOutgoingSyncs outgoing syncs in progress, then a fraction of at least:
 * 		(1 / (maxOutgoingSyncs + maxIncomingSyncsInc)) members will be willing to accept another incoming sync. So
 * 		even in the worst case, it should be possible to find a partner to sync with in about (maxOutgoingSyncs +
 * 		maxIncomingSyncsInc) tries, on average.
 * @param maxOutgoingSyncs
 * 		maximum number of simultaneous outgoing syncs initiated by me
 * @param logPath
 * 		path to log4j2.xml (which might not exist)
 */
@ConfigData
public record BasicConfig(
        @ConfigProperty(defaultValue = "configsUsed.txt") String configsUsedFilename,
        @ConfigProperty(defaultValue = "true") boolean verifyEventSigs,
        @ConfigProperty(defaultValue = "32") int numCryptoThreads,
        @ConfigProperty(defaultValue = "false") boolean showInternalStats,
        @ConfigProperty(defaultValue = "false") boolean verboseStatistics,
        @ConfigProperty(defaultValue = "false") boolean requireStateLoad,
        @ConfigProperty(defaultValue = "1") int signedStateFreq,
        @ConfigProperty(defaultValue = "10000") int maxEventQueueForCons,
        @ConfigProperty(defaultValue = "100000") int throttleTransactionQueueSize,
        @ConfigProperty(defaultValue = "false") boolean throttle7,
        @ConfigProperty(defaultValue = "1.5") double throttle7threshold,
        @ConfigProperty(defaultValue = "0.05") double throttle7extra,
        @ConfigProperty(defaultValue = "104857600") int throttle7maxBytes,
        @ConfigProperty(defaultValue = "40") int numConnections,
        @ConfigProperty(defaultValue = "8192") int bufferSize,
        @ConfigProperty(defaultValue = "10") double halfLife,
        @ConfigProperty(defaultValue = "true") boolean logStack,
        @ConfigProperty(defaultValue = "true") boolean doUpnp,
        @ConfigProperty(defaultValue = "true") boolean useLoopbackIp,
        @ConfigProperty(defaultValue = "true") boolean tcpNoDelay,
        @ConfigProperty(defaultValue = "5000") int timeoutSyncClientSocket,
        @ConfigProperty(defaultValue = "5000") int timeoutSyncClientConnect,
        @ConfigProperty(defaultValue = "5000") int timeoutServerAcceptConnect,
        @ConfigProperty(defaultValue = "1000") int deadlockCheckPeriod,
        @ConfigProperty(defaultValue = "500") int sleepHeartbeat,
        @ConfigProperty(defaultValue = "200") long delayShuffle,
        @ConfigProperty(value = "callerSkipsBeforeSleep", defaultValue = "30") long callerSkipsBeforeSleep,
        @ConfigProperty(value = "sleepCallerSkips", defaultValue = "50") long sleepCallerSkips,
        @ConfigProperty(value = "statsSkipSeconds", defaultValue = "60") double statsSkipSeconds,
        @ConfigProperty(value = "threadPrioritySync", defaultValue = "5") int threadPrioritySync,
        @ConfigProperty(value = "threadPriorityNonSync", defaultValue = "5") int threadPriorityNonSync,
        @ConfigProperty(value = "maxAddressSizeAllowed", defaultValue = "1024") int maxAddressSizeAllowed,
        @ConfigProperty(value = "freezeSecondsAfterStartup", defaultValue = "10") int freezeSecondsAfterStartup,
        @ConfigProperty(value = "loadKeysFromPfxFiles", defaultValue = "true") boolean loadKeysFromPfxFiles,
        @ConfigProperty(value = "maxTransactionBytesPerEvent", defaultValue = "245760") int maxTransactionBytesPerEvent,
        @ConfigProperty(value = "maxTransactionCountPerEvent", defaultValue = "245760") int maxTransactionCountPerEvent,
        @ConfigProperty(value = "emergencyStateFileName", defaultValue = "emergencyRecovery.csv")
                String emergencyStateFileName,
        @ConfigProperty(value = "eventIntakeQueueSize", defaultValue = "10000") int eventIntakeQueueSize,
        @ConfigProperty(value = "checkSignedStateFromDisk", defaultValue = "false") boolean checkSignedStateFromDisk,
        @ConfigProperty(value = "randomEventProbability", defaultValue = "0") int randomEventProbability,
        @ConfigProperty(value = "rescueChildlessInverseProbability", defaultValue = "10")
                int rescueChildlessInverseProbability,
        @ConfigProperty(value = "runPauseCheckTimer", defaultValue = "false") boolean runPauseCheckTimer,
        @ConfigProperty(value = "enableEventStreaming", defaultValue = "false") boolean enableEventStreaming,
        @ConfigProperty(value = "eventStreamQueueCapacity", defaultValue = "500") int eventStreamQueueCapacity,
        @ConfigProperty(value = "eventsLogPeriod", defaultValue = "60") long eventsLogPeriod,
        @ConfigProperty(value = "eventsLogDir", defaultValue = "./eventstreams") String eventsLogDir,
        @ConfigProperty(value = "threadDumpPeriodMs", defaultValue = "0") long threadDumpPeriodMs,
        @ConfigProperty(value = "threadDumpLogDir", defaultValue = "data/threadDump") String threadDumpLogDir,
        @ConfigProperty(value = "JVMPauseDetectorSleepMs", defaultValue = "1000") int jVMPauseDetectorSleepMs,
        @ConfigProperty(value = "JVMPauseReportMs", defaultValue = "1000") int jVMPauseReportMs,
        @ConfigProperty(value = "enableStateRecovery", defaultValue = "false") boolean enableStateRecovery,
        @ConfigProperty(value = "playbackStreamFileDirectory", defaultValue = "") String playbackStreamFileDirectory,
        @ConfigProperty(value = "playbackEndTimeStamp", defaultValue = "") String playbackEndTimeStamp,
        @ConfigProperty(value = "gossipWithDifferentVersions", defaultValue = "false")
                boolean gossipWithDifferentVersions,
        @ConfigProperty(value = "enablePingTrans", defaultValue = "true") boolean enablePingTrans,
        @ConfigProperty(value = "pingTransFreq", defaultValue = "1") long pingTransFreq,
        @ConfigProperty(value = "staleEventPreventionThreshold", defaultValue = "5") int staleEventPreventionThreshold,
        @ConfigProperty(value = "eventIntakeQueueThrottleSize", defaultValue = "1000") int eventIntakeQueueThrottleSize,
        @ConfigProperty(value = "transactionMaxBytes", defaultValue = "6144") int transactionMaxBytes,
        @ConfigProperty(value = "useTLS", defaultValue = "true") boolean useTLS,
        @ConfigProperty(value = "socketIpTos", defaultValue = "-1") int socketIpTos,
        @ConfigProperty(value = "maxIncomingSyncsInc", defaultValue = "1") int maxIncomingSyncsInc,
        @ConfigProperty(value = "maxOutgoingSyncs", defaultValue = "2") int maxOutgoingSyncs,
        @ConfigProperty(value = "logPath", defaultValue = "log4j2.xml") Path logPath) {}
