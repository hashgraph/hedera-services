/*
 * Copyright (C) 2017-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.settings.ParsingUtils.parseDuration;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.SettingConstants.APPS_STRING;
import static com.swirlds.platform.SettingConstants.BUFFER_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CALLER_SKIPS_BEFORE_SLEEP_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CHECK_SIGNED_STATE_FROM_DISK_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CONFIG_TXT;
import static com.swirlds.platform.SettingConstants.CSV_APPEND_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CSV_FILE_NAME_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CSV_OUTPUT_FOLDER_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.CSV_WRITE_FREQUENCY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.DATA_STRING;
import static com.swirlds.platform.SettingConstants.DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.DELAY_SHUFFLE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.DISABLE_METRICS_OUTPUT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.DO_UPNP_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EMERGENCY_STATE_FILE_NAME_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.ENABLE_EVENT_STREAMING_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EVENTS_LOG_DIR_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EVENTS_LOG_PERIOD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EVENT_INTAKE_QUEUE_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EVENT_INTAKE_QUEUE_THROTTLE_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.EVENT_STREAM_QUEUE_CAPACITY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.FREEZE_SECONDS_AFTER_STARTUP_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.GOSSIP_WITH_DIFFERENT_VERSIONS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.HALF_LIFE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.JVM_PAUSE_DETECTOR_SLEEP_MS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.JVM_PAUSE_REPORT_MS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.KEYS_STRING;
import static com.swirlds.platform.SettingConstants.LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.LOG4J2_CONFIG_FILE;
import static com.swirlds.platform.SettingConstants.LOG_STACK_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_EVENT_QUEUE_FOR_CONS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_INCOMING_SYNCS_INC_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_OUTGOING_SYNCS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.NUM_CONNECTIONS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.NUM_CRYPTO_THREADS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.PROMETHEUS_ENDPOINT_ENABLED_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.PROMETHEUS_ENDPOINT_MAX_BACKLOG_ALLOWED_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.PROMETHEUS_ENDPOINT_PORT_NUMBER_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.RANDOM_EVENT_PROBABILITY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.REQUIRE_STATE_LOAD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.RESCUE_CHILDLESS_INVERSE_PROBABILITY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.RUN_PAUSE_CHECK_TIMER_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SAVED_STRING;
import static com.swirlds.platform.SettingConstants.SETTINGS_TXT;
import static com.swirlds.platform.SettingConstants.SHOW_INTERNAL_STATS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SIGNED_STATE_FREQ_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SLEEP_CALLER_SKIPS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SLEEP_HEARTBEAT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SOCKET_IP_TOS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.STALE_EVENT_PREVENTION_THRESHOLD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.STATS_BUFFER_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.STATS_RECENT_SECONDS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.STATS_SKIP_SECONDS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TCP_NO_DELAY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_DUMP_LOG_DIR_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_DUMP_PERIOD_MS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_PRIORITY_SYNC_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TIMEOUT_SERVER_ACCEPT_CONNECT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TIMEOUT_SYNC_CLIENT_CONNECT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TIMEOUT_SYNC_CLIENT_SOCKET_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TRANSACTION_MAX_BYTES_DEFAULT_VALUES;
import static com.swirlds.platform.SettingConstants.USE_LOOPBACK_IP_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.USE_TLS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.VERBOSE_STATISTICS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.VERIFY_EVENT_SIGS_DEFAULT_VALUE;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.common.settings.SettingsException;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.config.api.Configuration;
import com.swirlds.fchashmap.FCHashMapSettingsFactory;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.internal.SubSetting;
import com.swirlds.platform.reconnect.ReconnectSettingsImpl;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This purely-static class holds global settings that control how the Platform and sync processes operate.
 * If the file sdk/settings.txt exists, then it will read the settings from it, to override one or more of
 * the default settings (and to override settings in config.txt). The Browser should call the loadSettings()
 * method to read that file, before it instantiates any Platform objects (or anything else).
 *
 * Any field that is intended to be a "setting" should be non-final. The settings.txt file will not change
 * any of the fields. But it will change all of the final fields (except maxIncomingSyncs, which is a
 * special case which is calculated from maxOutgoingSyncs, and cannot be changed directly from
 * settings.txt).
 *
 * After the config.txt and settings.txt files have been read and the Platform objects instantiated, the
 * Browser should then call writeSettings() to write all the final settings values to settingsUsed.txt
 * (though only if settings.txt exists).
 *
 * @deprecated will be replaced by the {@link Configuration} API in near future. If you need to
 * 		use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class Settings {

    // The following paths are for 4 files and 2 directories, such as:
    // /FULL/PATH/sdk/config.txt
    // /FULL/PATH/sdk/settings.txt
    // /FULL/PATH/sdk/settingsUsed.txt
    // /FULL/PATH/sdk/log4j2.xml
    // /FULL/PATH/sdk/data/keys/
    // /FULL/PATH/sdk/data/apps/

    // useful run configuration arguments for debugging:
    // -XX:+HeapDumpOnOutOfMemoryError
    // -Djavax.net.debug=ssl,handshake

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(Settings.class);

    private static final Settings INSTANCE = new Settings();
    /** path to config.txt (which might not exist) */
    private final Path configPath = getAbsolutePath(CONFIG_TXT);
    /** path to settings.txt (which might not exist) */
    private final Path settingsPath = getAbsolutePath(SETTINGS_TXT);
    /** the directory where the settings used file will be created on startup if and only if settings.txt exists */
    private final Path settingsUsedDir = getAbsolutePath();
    /** path to data/keys/ */
    private final Path keysDirPath = getAbsolutePath().resolve(DATA_STRING).resolve(KEYS_STRING);
    /** path to data/apps/ */
    private final Path appsDirPath = getAbsolutePath().resolve(DATA_STRING).resolve(APPS_STRING);

    ///////////////////////////////////////////
    // settings from settings.txt file
    /** priority for threads that don't sync (all but SyncCaller, SyncListener,SyncServer */
    private final int threadPriorityNonSync = THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE;
    /** path to log4j2.xml (which might not exist) */
    private Path logPath = rethrowIO(() -> getAbsolutePath(LOG4J2_CONFIG_FILE));
    /** verify event signatures (rather than just trusting they are correct)? */
    private boolean verifyEventSigs = VERIFY_EVENT_SIGS_DEFAULT_VALUE;
    /** number of threads used to verify signatures and generate keys, in parallel */
    private int numCryptoThreads = NUM_CRYPTO_THREADS_DEFAULT_VALUE;
    /** show the user all statistics, including those with category "internal"? */
    private boolean showInternalStats = SHOW_INTERNAL_STATS_DEFAULT_VALUE;
    /** show expand statistics values, inlcude mean, min, max, stdDev */
    private boolean verboseStatistics = VERBOSE_STATISTICS_DEFAULT_VALUE;
    /** if set to true, the platform will fail to start if it fails to load a state from disk */
    private boolean requireStateLoad = REQUIRE_STATE_LOAD_DEFAULT_VALUE;
    /**
     * hash and sign a state every signedStateFreq rounds. 1 means that a state will be signed every round, 2 means
     * every other round, and so on. If the value is 0 or less, no states will be signed
     */
    private int signedStateFreq = SIGNED_STATE_FREQ_DEFAULT_VALUE;
    /** max events that can be put in the forCons queue (q2) in ConsensusRoundHandler (0 for infinity) */
    private int maxEventQueueForCons = MAX_EVENT_QUEUE_FOR_CONS_DEFAULT_VALUE;
    /**
     * Stop accepting new non-system transactions into the 4 transaction queues if any of them have more
     * than this many.
     */
    private int throttleTransactionQueueSize = THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE;
    /** number of connections maintained by each member (syncs happen on random connections from that set */
    private int numConnections = NUM_CONNECTIONS_DEFAULT_VALUE; // probably 40 is a good number
    /** maximum number of simultaneous outgoing syncs initiated by me */
    private int maxOutgoingSyncs = MAX_OUTGOING_SYNCS_DEFAULT_VALUE;
    /**
     * maximum number of simultaneous incoming syncs initiated by others, minus maxOutgoingSyncs. If there
     * is a moment where each member has maxOutgoingSyncs outgoing syncs in progress, then a fraction of at
     * least:
     *
     * <pre>
     * (1 / (maxOutgoingSyncs + maxIncomingSyncsInc))
     * </pre>
     *
     * members will be willing to accept another incoming sync. So even in the worst case, it should be
     * possible to find a partner to sync with in about (maxOutgoingSyncs + maxIncomingSyncsInc) tries, on
     * average.
     */
    private int maxIncomingSyncsInc = MAX_INCOMING_SYNCS_INC_DEFAULT_VALUE;
    /** for BufferedInputStream and BufferedOutputStream for syncing */
    private int bufferSize = BUFFER_SIZE_DEFAULT_VALUE;
    /**
     * The IP_TOS to set for a socket, from 0 to 255, or -1 to not set one. This number (if not -1) will be
     * part of every TCP/IP packet, and is normally ignored by internet routers, but it is possible to make
     * routers change their handling of packets based on this number, such as for providing different
     * Quality of Service (QoS).
     *
     * @see <a href="https://en.wikipedia.org/wiki/Type_of_service">Type of Service</a>
     */
    private int socketIpTos = SOCKET_IP_TOS_DEFAULT_VALUE;
    /** half life of some of the various statistics (give half the weight to the last halfLife seconds) */
    private double halfLife = HALF_LIFE_DEFAULT_VALUE;

    /** when converting an exception to a string for logging, should it include the stack trace? */
    private boolean logStack = LOG_STACK_DEFAULT_VALUE;
    /** should TLS be turned on, rather than making all sockets unencrypted? */
    private boolean useTLS = USE_TLS_DEFAULT_VALUE;
    /** should this set up uPnP port forwarding on the router once every 60 seconds? */
    private boolean doUpnp = DO_UPNP_DEFAULT_VALUE;
    /** should be set to true when using the internet simulator */
    private boolean useLoopbackIp = USE_LOOPBACK_IP_DEFAULT_VALUE;
    /** if true, then Nagel's algorithm is disabled, which helps latency, hurts bandwidth usage */
    private boolean tcpNoDelay = TCP_NO_DELAY_DEFAULT_VALUE;
    /** timeout when waiting for data */
    private int timeoutSyncClientSocket = TIMEOUT_SYNC_CLIENT_SOCKET_DEFAULT_VALUE;
    /** timeout when establishing a connection */
    private int timeoutSyncClientConnect = TIMEOUT_SYNC_CLIENT_CONNECT_DEFAULT_VALUE;
    /** timeout when server is waiting for another member to create a connection */
    private int timeoutServerAcceptConnect = TIMEOUT_SERVER_ACCEPT_CONNECT_DEFAULT_VALUE;
    /** check for deadlocks every this many milliseconds (-1 for never) */
    private int deadlockCheckPeriod = DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE;
    /** send a heartbeat byte on each comm channel to keep it open, every this many milliseconds */
    private int sleepHeartbeat = SLEEP_HEARTBEAT_DEFAULT_VALUE;
    /**
     * the working state (stateWork) resets to a copy of the consensus state (stateCons) (which is called a
     * shuffle) when its queue is empty and the two are equal, but never twice within this many milliseconds
     */
    private long delayShuffle = DELAY_SHUFFLE_DEFAULT_VALUE;
    /** sleep sleepCallerSkips ms after the caller fails this many times to call a random member */
    private long callerSkipsBeforeSleep = CALLER_SKIPS_BEFORE_SLEEP_DEFAULT_VALUE;
    /** caller sleeps this many milliseconds if it failed to connect to callerSkipsBeforeSleep in a row */
    private long sleepCallerSkips = SLEEP_CALLER_SKIPS_DEFAULT_VALUE;
    /** number of bins to store for the history (in StatsBuffer etc.) */
    private int statsBufferSize = STATS_BUFFER_SIZE_DEFAULT_VALUE;
    /** number of seconds covered by "recent" history (in StatsBuffer etc.) */
    private double statsRecentSeconds = STATS_RECENT_SECONDS_DEFAULT_VALUE;
    /** number of seconds that the "all" history window skips at the start */
    private double statsSkipSeconds = STATS_SKIP_SECONDS_DEFAULT_VALUE;
    /** priority for threads that sync (in SyncCaller, SyncListener, SyncServer) */
    private int threadPrioritySync = THREAD_PRIORITY_SYNC_DEFAULT_VALUE; // Thread.MAX_PRIORITY;
    /** maximum number of bytes allowed in a transaction */
    private int transactionMaxBytes = TRANSACTION_MAX_BYTES_DEFAULT_VALUES;
    /** the maximum number of address allowed in a address book, the same as the maximum allowed network size */
    private int maxAddressSizeAllowed = MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE;
    /**
     * do not create events for this many seconds after the platform has started (0 or less to not freeze at
     * startup)
     */
    private int freezeSecondsAfterStartup = FREEZE_SECONDS_AFTER_STARTUP_DEFAULT_VALUE;
    /**
     * When enabled, the platform will try to load node keys from .pfx files located in {@link #keysDirPath}. If even a
     * single key is missing, the platform will warn and exit.
     *
     * If disabled, the platform will generate keys deterministically.
     */
    private boolean loadKeysFromPfxFiles = LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE;
    /**
     * the maximum number of bytes that a single event may contain not including the event headers
     * if a single transaction exceeds this limit then the event will contain the single transaction only
     */
    private int maxTransactionBytesPerEvent = MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE;
    /** the maximum number of transactions that a single event may contain */
    private int maxTransactionCountPerEvent = MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE;
    /**
     * The absolute or relative folder path where all the statistics CSV files will be written. If this value is null or
     * an empty string, the current folder selection behavior will be used (ie: the SDK base path).
     */
    private String csvOutputFolder = CSV_OUTPUT_FOLDER_DEFAULT_VALUE;
    /**
     * Disable all metrics-outputs. If {@code true}, this overrides all other specific settings concerning
     * metrics-output.
     */
    private boolean disableMetricsOutput = DISABLE_METRICS_OUTPUT_DEFAULT_VALUE;
    /**
     * The prefix of the name of the CSV file that the platform will write statistics to. If this value is null or an
     * empty string, the platform will not write any statistics.
     */
    private String csvFileName = CSV_FILE_NAME_DEFAULT_VALUE;
    /**
     * The CSV file name of the emergency state recovery file
     */
    private String emergencyStateFileName = EMERGENCY_STATE_FILE_NAME_DEFAULT_VALUE;
    /**
     * The path to look for an emergency recovery file on node start. If a file is present in this directory at startup,
     * emergency recovery will begin.
     */
    private Path emergencyRecoveryFileLoadDir =
            getAbsolutePath().resolve(DATA_STRING).resolve(SAVED_STRING);
    /**
     * The frequency, in milliseconds, at which values are written to the statistics CSV file.
     */
    private int csvWriteFrequency = CSV_WRITE_FREQUENCY_DEFAULT_VALUE;
    /** Indicates whether statistics should be appended to the CSV file. */
    private boolean csvAppend = CSV_APPEND_DEFAULT_VALUE;
    /** Indicates if a prometheus endpoint should be offered **/
    private boolean prometheusEndpointEnabled = PROMETHEUS_ENDPOINT_ENABLED_DEFAULT_VALUE;
    /** Port of the Prometheus endpoint **/
    private int prometheusEndpointPortNumber = PROMETHEUS_ENDPOINT_PORT_NUMBER_DEFAULT_VALUE;
    /** Backlog of the Prometheus endpoint (= number of incoming TCP connections the system will queue) **/
    private int prometheusEndpointMaxBacklogAllowed = PROMETHEUS_ENDPOINT_MAX_BACKLOG_ALLOWED_DEFAULT_VALUE;

    /** The value for the event intake queue at which the node should stop syncing */
    private int eventIntakeQueueThrottleSize = EVENT_INTAKE_QUEUE_THROTTLE_SIZE_DEFAULT_VALUE;
    /**
     * The size of the event intake queue,
     * {@link QueueThreadConfiguration#UNLIMITED_CAPACITY} for unbounded.
     * It is best that this queue is large, but not unbounded. Filling it up can cause sync threads to drop TCP
     * connections, but leaving it unbounded can cause out of memory errors, even with the {@link
     * #eventIntakeQueueThrottleSize}, because syncs that started before the throttle engages can grow the queue to very
     * large sizes on larger networks.
     */
    private int eventIntakeQueueSize = EVENT_INTAKE_QUEUE_SIZE_DEFAULT_VALUE;
    /**
     * If true, the platform will recalculate the hash of the signed state and check it against the written hash. It
     * will also verify that the signatures are valid.
     */
    private boolean checkSignedStateFromDisk = CHECK_SIGNED_STATE_FROM_DISK_DEFAULT_VALUE;
    /**
     * The probability that after a sync, a node will create an event with a random other parent. The probability is
     * is 1 in X, where X is the value of randomEventProbability. A value of 0 means that a node will not create any
     * random events.
     *
     * This feature is used to get consensus on events with no descendants which are created by nodes who go offline.
     */
    private int randomEventProbability = RANDOM_EVENT_PROBABILITY_DEFAULT_VALUE;
    /**
     * A setting used to prevent a node from generating events that will probably become stale. This value is
     * multiplied by the address book size and compared to the number of events received in a sync.
     * If ( numEventsReceived > staleEventPreventionThreshold * addressBookSize ) then we will not create an event for
     * that sync, to reduce the probability of creating an event that will become stale.
     */
    private int staleEventPreventionThreshold = STALE_EVENT_PREVENTION_THRESHOLD_DEFAULT_VALUE;
    /**
     * The probability that we will create a child for a childless event.
     * The probability is 1 / X, where X is the value of rescueChildlessInverseProbability. A value of 0 means
     * that a node will not create any children for childless events.
     */
    private int rescueChildlessInverseProbability = RESCUE_CHILDLESS_INVERSE_PROBABILITY_DEFAULT_VALUE;

    ///////////////////////////////////////////
    // Beta Mirror Nodes
    /** Run a thread that checks if the JVM pauses for a long time */
    private boolean runPauseCheckTimer = RUN_PAUSE_CHECK_TIMER_DEFAULT_VALUE;

    ///////////////////////////////////////////
    // Setting for stream event
    /** enable stream event to server */
    private boolean enableEventStreaming = ENABLE_EVENT_STREAMING_DEFAULT_VALUE;
    /** capacity of the blockingQueue from which we take events and write to EventStream files */
    private int eventStreamQueueCapacity = EVENT_STREAM_QUEUE_CAPACITY_DEFAULT_VALUE;
    /** period of generating eventStream file */
    private long eventsLogPeriod = EVENTS_LOG_PERIOD_DEFAULT_VALUE;

    ///////////////////////////////////////////
    // Setting for thread dump
    /** eventStream files will be generated in this directory */
    private String eventsLogDir = EVENTS_LOG_DIR_DEFAULT_VALUE;
    /** period of generating thread dump file in the unit of milliseconds */
    private long threadDumpPeriodMs = THREAD_DUMP_PERIOD_MS_DEFAULT_VALUE;

    ///////////////////////////////////////////
    // Setting for JVMPauseDetectorThread
    /** thread dump files will be generated in this directory */
    private String threadDumpLogDir = THREAD_DUMP_LOG_DIR_DEFAULT_VALUE;
    /** period of JVMPauseDetectorThread sleeping in the unit of milliseconds */
    private int JVMPauseDetectorSleepMs = JVM_PAUSE_DETECTOR_SLEEP_MS_DEFAULT_VALUE;
    /** log an error when JVMPauseDetectorThread detect a pause greater than this many milliseconds */
    private int JVMPauseReportMs = JVM_PAUSE_REPORT_MS_DEFAULT_VALUE;
    /**
     * if set to false, the platform will refuse to gossip with a node which has a different version of either
     * platform or application
     */
    private boolean gossipWithDifferentVersions = GOSSIP_WITH_DIFFERENT_VERSIONS_DEFAULT_VALUE;

    /** settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors */
    private StateSettings state = new StateSettings();

    /**
     * settings controlling the reconnect feature, ie. enabled/disabled, fallen behind, etc
     */
    private ReconnectSettingsImpl reconnect = new ReconnectSettingsImpl();
    /**
     * Settings controlling FCHashMap.
     */
    private FCHashMapSettingsImpl fcHashMap = new FCHashMapSettingsImpl();
    /**
     * Settings controlling VirtualMap.
     */
    private VirtualMapSettingsImpl virtualMap = new VirtualMapSettingsImpl();
    /**
     * Settings controlling JasperDB.
     */
    private JasperDbSettingsImpl jasperDb = new JasperDbSettingsImpl();
    /**
     * Settings controlling MerkleDb.
     */
    private MerkleDbSettingsImpl merkleDb = new MerkleDbSettingsImpl();

    /** All chatter related settings */
    private ChatterSubSetting chatter = new ChatterSubSetting();

    private Settings() {}

    public static Settings getInstance() {
        return INSTANCE;
    }

    public static void main(final String[] args) {
        getInstance().loadSettings();
        getInstance().writeSettingsUsed();
    }

    public static void populateSettingsCommon() {
        SettingsCommon.maxTransactionCountPerEvent = getInstance().getMaxTransactionCountPerEvent();
        SettingsCommon.maxTransactionBytesPerEvent = getInstance().getMaxTransactionBytesPerEvent();
        SettingsCommon.maxAddressSizeAllowed = getInstance().getMaxAddressSizeAllowed();
        SettingsCommon.transactionMaxBytes = getInstance().getTransactionMaxBytes();
        SettingsCommon.halfLife = getInstance().getHalfLife();
        SettingsCommon.logStack = getInstance().isLogStack();
        SettingsCommon.showInternalStats = getInstance().isShowInternalStats();
        SettingsCommon.verboseStatistics = getInstance().isVerboseStatistics();

        ReconnectSettingsFactory.configure(getInstance().getReconnect());
        FCHashMapSettingsFactory.configure(getInstance().getFcHashMap());
        VirtualMapSettingsFactory.configure(getInstance().getVirtualMap());
        JasperDbSettingsFactory.configure(getInstance().getJasperDb());
        MerkleDbSettingsFactory.configure(getInstance().getMerkleDb());
    }

    /**
     * Split the given string on its commas, and trim each result
     *
     * @param line
     * 		the string of comma-separated values to split
     * @return the array of trimmed elements.
     */
    public static String[] splitLine(final String line) {
        final String[] elms = line.split(",");
        for (int i = 0; i < elms.length; i++) {
            elms[i] = elms[i].trim();
        }

        return elms;
    }

    public void writeSettingsUsed() {
        writeSettingsUsed(settingsUsedDir);
    }

    public void addSettingsUsed(final StringBuilder builder) {
        final String[][] settings = currSettings();
        builder.append(PlatformVersion.locateOrDefault().license());
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());

        builder.append(
                "The following are all the settings, as modified by settings.txt, but not reflecting any changes "
                        + "made by config.txt.");
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());
        for (final String[] pair : settings) {
            builder.append(String.format("%15s = %s%n", pair[1], pair[0]));
        }
    }

    /**
     * Write all the settings to the file settingsUsed.txt, some of which might have been changed by settings.txt.
     *
     * @param directory
     * 		the directory to write to
     */
    public void writeSettingsUsed(final Path directory) {
        final String[][] settings = currSettings();
        try (final BufferedWriter writer =
                Files.newBufferedWriter(directory.resolve(SettingConstants.SETTING_USED_FILENAME))) {
            writer.write(PlatformVersion.locateOrDefault().license());
            writer.write(System.lineSeparator());
            writer.write(System.lineSeparator());

            writer.write(
                    "The following are all the settings, as modified by settings.txt, but not reflecting any changes "
                            + "made by config.txt.");
            writer.write(System.lineSeparator());
            writer.write(System.lineSeparator());
            for (final String[] pair : settings) {
                writer.write(String.format("%15s = %s%n", pair[1], pair[0]));
            }
            writer.flush();
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Error in writing to settingsUsed.txt", e);
        }
    }

    /**
     * If the sdk/data/settings.txt file exists, then load settings from it. If it doesn't exist, keep the
     * existing settings. If it exists but a setting is missing, keep the default value for it. If a setting
     * is given multiple times, use the last one. If the file contains a setting name that doesn't exist,
     * complain to the command line.
     *
     * It is intended that this file will not normally exist. Most settings should be controlled by the
     * defaults set in this source file. The settings.txt file is only used for testing and debugging.
     */
    public void loadSettings() {
        loadSettings(settingsPath.toFile());
    }

    public void loadSettings(final Path path) {
        CommonUtils.throwArgNull(path, "path");
        loadSettings(path.toFile());
    }

    public void loadSettings(final File settingsFile) {
        CommonUtils.throwArgNull(settingsFile, "settingsFile");
        final Scanner scanner;
        if (!Files.exists(settingsFile.toPath())) {
            return; // normally, the file won't exist, so the defaults are used.
        }

        try {
            scanner = new Scanner(settingsFile, StandardCharsets.UTF_8.name());
        } catch (final FileNotFoundException e) { // this should never happen
            CommonUtils.tellUserConsole(
                    "The file " + Settings.getInstance().getSettingsPath() + " exists, but can't be opened. " + e);
            return;
        }

        CommonUtils.tellUserConsole("Reading the settings from the file:        " + settingsFile.getAbsolutePath());

        int count = 0;
        while (scanner.hasNextLine()) {
            final String originalLine = scanner.nextLine();
            String line = originalLine;
            final int pos = line.indexOf("#");
            if (pos > -1) {
                line = line.substring(0, pos);
            }
            line = line.trim();
            count++;
            if (!line.isEmpty()) {
                final String[] pars = splitLine(line);
                if (pars.length > 0) { // ignore empty lines
                    try {
                        if (!handleSetting(pars)) {
                            CommonUtils.tellUserConsole(
                                    "bad name of setting in settings.txt line " + count + ": " + originalLine);
                        }
                    } catch (final Exception e) {
                        CommonUtils.tellUserConsole(
                                "syntax error in settings.txt on line " + count + ":    " + originalLine);
                        scanner.close();
                        return;
                    }
                }
            }
        }
        scanner.close();

        validateSettings();
    }

    /**
     * validate the settings read in from the settings.txt file
     */
    private void validateSettings() {
        // if the settings allow a transaction larger than the maximum event size
        if (maxTransactionBytesPerEvent < transactionMaxBytes) {
            logger.error(
                    STARTUP.getMarker(),
                    "Settings Mismatch: transactionMaxBytes ({}) is larger than "
                            + "maxTransactionBytesPerEvent ({}), truncating transactionMaxBytes to {}.",
                    transactionMaxBytes,
                    maxTransactionBytesPerEvent,
                    maxTransactionBytesPerEvent);

            transactionMaxBytes = maxTransactionBytesPerEvent;
        }
    }

    /**
     * handle a single line from the settings.txt file. The line is split by commas, so none of the
     * individual strings or values should have commas in them. The first token on the line is intended to
     * state what setting is being changed, and the rest is the value for that setting.
     *
     * @param pars
     * 		the parameters on that line, split by commas
     * @return true if the line is a valid setting assignment
     */
    private boolean handleSetting(final String[] pars) {
        String name = pars[0];
        String subName = null;
        if (name.contains(".")) {
            // if the name contains a dot (.), then we need to set a variable that is inside an object
            final String[] split = name.split("\\.");
            name = split[0];
            subName = split[1];
        }
        final String val = pars.length > 1 ? pars[1].trim() : ""; // the first parameter passed in, or "" if none
        boolean good = false; // is name a valid name of a non-final static field in Settings?
        final Field field = getFieldByName(Settings.class.getDeclaredFields(), name);
        if (field != null && !Modifier.isFinal(field.getModifiers())) {
            try {
                if (subName == null) {
                    good = setValue(field, this, val);
                } else {
                    final Field subField = getFieldByName(field.getType().getDeclaredFields(), subName);
                    if (subField != null) {
                        good = setValue(subField, field.get(this), val);
                    }
                }
            } catch (final IllegalArgumentException | IllegalAccessException | SettingsException e) {
                logger.error(EXCEPTION.getMarker(), "illegal line in settings.txt: {}, {}  {}", pars[0], pars[1], e);
            }
        }

        if (!good) {
            final String err = "WARNING: " + pars[0] + " is not a valid setting name.";
            // this only happens if settings.txt exist, so it's internal, not users, so print it
            CommonUtils.tellUserConsole(err);
            logger.warn(STARTUP.getMarker(), err);
            return false;
        }
        return true;
    }

    /**
     * Finds a field from the array with the given name
     *
     * @param fields
     * 		the fields to search in
     * @param name
     * 		the name of the field to look for
     * @return the field with the name supplied, or null if such a field cannot be found
     */
    private Field getFieldByName(final Field[] fields, final String name) {
        for (final Field f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Sets the value via reflection, converting the string value into the appropriate type
     *
     * @param field
     * 		the field to set
     * @param object
     * 		the object in which to set the field, should be null if the field is static
     * @param value
     * 		the value to set it to
     * @return true if the field was set, false otherwise
     * @throws IllegalAccessException
     * 		if this Field object is enforcing Java language access control and the
     * 		underlying field is either inaccessible or final.
     */
    private boolean setValue(final Field field, final Object object, final String value) throws IllegalAccessException {
        final Class<?> t = field.getType();
        if (t == String.class) {
            field.set(object, value);
            return true;
        } else if (t == char.class) {
            field.set(object, value.charAt(0));
            return true;
        } else if (t == byte.class) {
            field.set(object, Byte.parseByte(value));
            return true;
        } else if (t == short.class) {
            field.set(object, Short.parseShort(value));
            return true;
        } else if (t == int.class) {
            field.set(object, Integer.parseInt(value));
            return true;
        } else if (t == long.class) {
            field.set(object, Long.parseLong(value));
            return true;
        } else if (t == boolean.class) {
            field.set(object, Utilities.parseBoolean(value));
            return true;
        } else if (t == float.class) {
            field.set(object, Float.parseFloat(value));
            return true;
        } else if (t == double.class) {
            field.set(object, Double.parseDouble(value));
            return true;
        } else if (t == Duration.class) {
            field.set(object, parseDuration(value));
            return true;
        }
        return false;
    }

    /**
     * Return all the current settings, as a 2D array of strings, where the first column is the name of the
     * setting, and the second column is the value.
     *
     * @return the current settings
     */
    private String[][] currSettings() {
        final Field[] fields = Settings.class.getDeclaredFields();
        final List<String[]> list = new ArrayList<>();
        for (final Field f : fields) {
            // every non-setting field should be final, so the following deals with the correct fields
            if (!Modifier.isFinal(f.getModifiers())) {
                try {
                    if (SubSetting.class.isAssignableFrom(f.getType())) {
                        final Field[] subFields = f.getType().getDeclaredFields();
                        for (final Field subField : subFields) {
                            final Object subFieldValue = subField.get(f.get(this));
                            list.add(new String[] {
                                f.getName() + "." + subField.getName(),
                                subFieldValue == null ? "null" : subFieldValue.toString()
                            });
                        }
                    } else {
                        list.add(new String[] {f.getName(), f.get(this).toString()});
                    }
                } catch (final IllegalArgumentException | IllegalAccessException e) {
                    logger.error(EXCEPTION.getMarker(), "error while reading settings.txt", e);
                }
            }
        }
        return list.toArray(new String[0][0]);
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public Path getSettingsUsedDir() {
        return settingsUsedDir;
    }

    public Path getKeysDirPath() {
        return keysDirPath;
    }

    public Path getAppsDirPath() {
        return appsDirPath;
    }

    public Path getLogPath() {
        return logPath;
    }

    public void setLogPath(final Path logPath) {
        this.logPath = logPath;
    }

    public boolean isVerifyEventSigs() {
        return verifyEventSigs;
    }

    public int getNumCryptoThreads() {
        return numCryptoThreads;
    }

    public boolean isShowInternalStats() {
        return showInternalStats;
    }

    public boolean isVerboseStatistics() {
        return verboseStatistics;
    }

    public StateSettings getState() {
        return state;
    }

    public boolean isRequireStateLoad() {
        return requireStateLoad;
    }

    public void setRequireStateLoad(final boolean value) {
        requireStateLoad = value;
    }

    public int getSignedStateFreq() {
        return signedStateFreq;
    }

    public int getMaxEventQueueForCons() {
        return maxEventQueueForCons;
    }

    public int getThrottleTransactionQueueSize() {
        return throttleTransactionQueueSize;
    }

    public int getNumConnections() {
        return numConnections;
    }

    public int getMaxOutgoingSyncs() {
        return maxOutgoingSyncs;
    }

    public void setMaxOutgoingSyncs(final int maxOutgoingSyncs) {
        this.maxOutgoingSyncs = maxOutgoingSyncs;
    }

    public int getMaxIncomingSyncsInc() {
        return maxIncomingSyncsInc;
    }

    public void setMaxIncomingSyncsInc(final int maxIncomingSyncsInc) {
        this.maxIncomingSyncsInc = maxIncomingSyncsInc;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getSocketIpTos() {
        return socketIpTos;
    }

    public void setSocketIpTos(final int socketIpTos) {
        this.socketIpTos = socketIpTos;
    }

    public double getHalfLife() {
        return halfLife;
    }

    public boolean isLogStack() {
        return logStack;
    }

    public boolean isUseTLS() {
        return useTLS;
    }

    public void setUseTLS(final boolean useTLS) {
        this.useTLS = useTLS;
    }

    public boolean isDoUpnp() {
        return doUpnp;
    }

    public boolean isUseLoopbackIp() {
        return useLoopbackIp;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public int getTimeoutSyncClientSocket() {
        return timeoutSyncClientSocket;
    }

    public int getTimeoutSyncClientConnect() {
        return timeoutSyncClientConnect;
    }

    public int getTimeoutServerAcceptConnect() {
        return timeoutServerAcceptConnect;
    }

    public int getDeadlockCheckPeriod() {
        return deadlockCheckPeriod;
    }

    public int getSleepHeartbeat() {
        return sleepHeartbeat;
    }

    public long getDelayShuffle() {
        return delayShuffle;
    }

    public long getCallerSkipsBeforeSleep() {
        return callerSkipsBeforeSleep;
    }

    public long getSleepCallerSkips() {
        return sleepCallerSkips;
    }

    public double getStatsSkipSeconds() {
        return statsSkipSeconds;
    }

    public int getThreadPrioritySync() {
        return threadPrioritySync;
    }

    public int getThreadPriorityNonSync() {
        return threadPriorityNonSync;
    }

    public int getTransactionMaxBytes() {
        return transactionMaxBytes;
    }

    public void setTransactionMaxBytes(final int transactionMaxBytes) {
        this.transactionMaxBytes = transactionMaxBytes;
    }

    public int getMaxAddressSizeAllowed() {
        return maxAddressSizeAllowed;
    }

    public int getFreezeSecondsAfterStartup() {
        return freezeSecondsAfterStartup;
    }

    public boolean isLoadKeysFromPfxFiles() {
        return loadKeysFromPfxFiles;
    }

    public int getMaxTransactionBytesPerEvent() {
        return maxTransactionBytesPerEvent;
    }

    public int getMaxTransactionCountPerEvent() {
        return maxTransactionCountPerEvent;
    }

    public ReconnectSettingsImpl getReconnect() {
        return reconnect;
    }

    public FCHashMapSettingsImpl getFcHashMap() {
        return fcHashMap;
    }

    public VirtualMapSettingsImpl getVirtualMap() {
        return virtualMap;
    }

    public JasperDbSettingsImpl getJasperDb() {
        return jasperDb;
    }

    public MerkleDbSettingsImpl getMerkleDb() {
        return merkleDb;
    }

    public String getCsvOutputFolder() {
        return csvOutputFolder;
    }

    public boolean isDisableMetricsOutput() {
        return disableMetricsOutput;
    }

    public String getCsvFileName() {
        return csvFileName;
    }

    public int getCsvWriteFrequency() {
        return csvWriteFrequency;
    }

    public boolean isCsvAppend() {
        return csvAppend;
    }

    public boolean getPrometheusEndpointEnabled() {
        return prometheusEndpointEnabled;
    }

    public int getPrometheusEndpointPortNumber() {
        return prometheusEndpointPortNumber;
    }

    public int getPrometheusEndpointMaxBacklogAllowed() {
        return prometheusEndpointMaxBacklogAllowed;
    }

    public int getEventIntakeQueueThrottleSize() {
        return eventIntakeQueueThrottleSize;
    }

    public void setEventIntakeQueueThrottleSize(final int eventIntakeQueueThrottleSize) {
        this.eventIntakeQueueThrottleSize = eventIntakeQueueThrottleSize;
    }

    public int getEventIntakeQueueSize() {
        return eventIntakeQueueSize;
    }

    public boolean isCheckSignedStateFromDisk() {
        return checkSignedStateFromDisk;
    }

    public void setCheckSignedStateFromDisk(final boolean value) {
        checkSignedStateFromDisk = value;
    }

    public int getRandomEventProbability() {
        return randomEventProbability;
    }

    public int getStaleEventPreventionThreshold() {
        return staleEventPreventionThreshold;
    }

    public void setStaleEventPreventionThreshold(final int staleEventPreventionThreshold) {
        this.staleEventPreventionThreshold = staleEventPreventionThreshold;
    }

    public int getRescueChildlessInverseProbability() {
        return rescueChildlessInverseProbability;
    }

    public boolean isRunPauseCheckTimer() {
        return runPauseCheckTimer;
    }

    public boolean isEnableEventStreaming() {
        return enableEventStreaming;
    }

    public int getEventStreamQueueCapacity() {
        return eventStreamQueueCapacity;
    }

    public long getEventsLogPeriod() {
        return eventsLogPeriod;
    }

    public String getEventsLogDir() {
        return eventsLogDir;
    }

    public long getThreadDumpPeriodMs() {
        return threadDumpPeriodMs;
    }

    public String getThreadDumpLogDir() {
        return threadDumpLogDir;
    }

    public int getJVMPauseDetectorSleepMs() {
        return JVMPauseDetectorSleepMs;
    }

    public int getJVMPauseReportMs() {
        return JVMPauseReportMs;
    }

    public ChatterSubSetting getChatter() {
        return chatter;
    }

    public boolean isGossipWithDifferentVersions() {
        return gossipWithDifferentVersions;
    }

    public String getEmergencyRecoveryStateFileName() {
        return emergencyStateFileName;
    }

    public Path getEmergencyRecoveryFileLoadDir() {
        return emergencyRecoveryFileLoadDir;
    }
}
