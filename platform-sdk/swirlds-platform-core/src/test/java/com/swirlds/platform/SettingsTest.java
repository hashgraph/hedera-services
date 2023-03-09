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

package com.swirlds.platform;

import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_FULL_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_INDEX_REBUILDING_ENFORCED;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_FILE_SIZE_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_GB_RAM_FOR_MERGING;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUM_OF_KEYS;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MERGE_ACTIVATED_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MOVE_LIST_CHUNK_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_SMALL_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;
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
import static com.swirlds.platform.SettingConstants.DO_UPNP_DEFAULT_VALUE;
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
import static com.swirlds.platform.SettingConstants.RANDOM_EVENT_PROBABILITY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.REQUIRE_STATE_LOAD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.RESCUE_CHILDLESS_INVERSE_PROBABILITY_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.RUN_PAUSE_CHECK_TIMER_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SETTINGS_TXT;
import static com.swirlds.platform.SettingConstants.SHOW_INTERNAL_STATS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SIGNED_STATE_FREQ_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SLEEP_CALLER_SKIPS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SLEEP_HEARTBEAT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SOCKET_IP_TOS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.STALE_EVENT_PREVENTION_THRESHOLD_DEFAULT_VALUE;
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
import static com.swirlds.platform.SettingConstants.TRANS_THROTTLE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.USE_LOOPBACK_IP_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.USE_TLS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.VERBOSE_STATISTICS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.VERIFY_EVENT_SIGS_DEFAULT_VALUE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_FLUSH_INTERVAL;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_FLUSH_THROTTLE_STEP_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PERCENT_CLEANER_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PERCENT_HASH_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.UNIT_FRACTION_PERCENT;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.settings.ParsingUtils;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.reconnect.ReconnectSettingsImpl;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.test.framework.TestTypeTags;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loading settings from an not existing files does not throw an exception")
    public void checkNotExistingFile() {
        // given
        final Settings settings = Settings.getInstance();
        final File notExistingFile =
                new File(new StringBuffer(SettingsTest.class.getPackageName().replace('.', '/'))
                        .append("/not-existing.txt")
                        .toString());

        // then
        Assertions.assertDoesNotThrow(() -> settings.loadSettings(notExistingFile));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loading settings from an empty files does not throw an exception")
    public void checkEmptyFile() {
        // given
        final Settings settings = Settings.getInstance();
        final File emptyFile =
                new File(SettingsTest.class.getResource("settings1.txt").getFile());

        // then
        Assertions.assertTrue(emptyFile.exists());
        Assertions.assertDoesNotThrow(() -> settings.loadSettings(emptyFile));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that null value for file not allowed")
    public void checkNullFile() {
        // given
        final Settings settings = Settings.getInstance();
        final File nullFile = null;

        // when
        Assertions.assertThrows(IllegalArgumentException.class, () -> settings.loadSettings(nullFile));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that null value for path not allowed")
    public void checkNullPath() {
        // given
        final Settings settings = Settings.getInstance();
        final Path nullPath = null;

        // when
        Assertions.assertThrows(IllegalArgumentException.class, () -> settings.loadSettings(nullPath));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that settings are part of saved settings")
    public void checkStoreSetting() throws IOException {
        // given
        final Settings settings = Settings.getInstance();
        final String propertyName = "socketIpTos";
        final Path savedSettingsDirectory = Files.createTempDirectory("settings-test");

        // when
        settings.setSocketIpTos(123);
        settings.writeSettingsUsed(savedSettingsDirectory);
        final Path savedSettingsFile = savedSettingsDirectory.resolve(SettingConstants.SETTING_USED_FILENAME);
        final String savedValue = readValueFromFile(savedSettingsFile, propertyName);

        // then
        Assertions.assertEquals("123", savedValue);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that property is loaded from file")
    public void checkLoadSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings2.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        final int oldValue = settings.getSocketIpTos();
        settings.loadSettings(settingsFile);

        // then
        Assertions.assertNotEquals(123, oldValue);
        Assertions.assertEquals(123, settings.getSocketIpTos());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that sub-settings are part of saved settings")
    public void checkStoreSubSetting() throws IOException {
        // given
        final Settings settings = Settings.getInstance();
        final String propertyName = "reconnect.asyncStreamBufferSize";
        final Path savedSettingsDirectory = Files.createTempDirectory("settings-test");

        // when
        settings.getReconnect().asyncStreamBufferSize = 123;
        settings.writeSettingsUsed(savedSettingsDirectory);
        final Path savedSettingsFile = savedSettingsDirectory.resolve(SettingConstants.SETTING_USED_FILENAME);
        final String savedValue = readValueFromFile(savedSettingsFile, propertyName);

        // then
        Assertions.assertEquals("123", savedValue);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that sub-property is loaded from file")
    public void checkLoadSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings3.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.getReconnect().asyncStreams = false;
        settings.loadSettings(settingsFile);

        // then
        Assertions.assertTrue(settings.getReconnect().asyncStreams);
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default settings are retrieved correctly")
    public void checkGetDefaultSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final Path configPath = FileUtils.getAbsolutePath(CONFIG_TXT);
        final Path settingsPath = FileUtils.getAbsolutePath(SETTINGS_TXT);
        final Path keysDirectoryPath =
                FileUtils.getAbsolutePath().resolve(DATA_STRING).resolve(KEYS_STRING);
        final Path appsDirectoryPath =
                FileUtils.getAbsolutePath().resolve(DATA_STRING).resolve(APPS_STRING);
        final Path logPath = FileUtils.rethrowIO(() -> FileUtils.getAbsolutePath(LOG4J2_CONFIG_FILE));
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        // then
        Assertions.assertEquals(configPath, settings.getConfigPath());
        Assertions.assertEquals(settingsPath, settings.getSettingsPath());
        Assertions.assertEquals(keysDirectoryPath, settings.getKeysDirPath());
        Assertions.assertEquals(appsDirectoryPath, settings.getAppsDirPath());
        Assertions.assertEquals(logPath, settings.getLogPath());
        Assertions.assertEquals(VERIFY_EVENT_SIGS_DEFAULT_VALUE, settings.isVerifyEventSigs());
        Assertions.assertEquals(NUM_CRYPTO_THREADS_DEFAULT_VALUE, settings.getNumCryptoThreads());
        Assertions.assertEquals(SHOW_INTERNAL_STATS_DEFAULT_VALUE, settings.isShowInternalStats());
        Assertions.assertEquals(VERBOSE_STATISTICS_DEFAULT_VALUE, settings.isVerboseStatistics());
        Assertions.assertEquals(REQUIRE_STATE_LOAD_DEFAULT_VALUE, settings.isRequireStateLoad());
        Assertions.assertEquals(SIGNED_STATE_FREQ_DEFAULT_VALUE, settings.getSignedStateFreq());
        Assertions.assertEquals(MAX_EVENT_QUEUE_FOR_CONS_DEFAULT_VALUE, settings.getMaxEventQueueForCons());
        Assertions.assertEquals(
                THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE, settings.getThrottleTransactionQueueSize());
        Assertions.assertEquals(NUM_CONNECTIONS_DEFAULT_VALUE, settings.getNumConnections());
        Assertions.assertEquals(MAX_OUTGOING_SYNCS_DEFAULT_VALUE, settings.getMaxOutgoingSyncs());
        Assertions.assertEquals(MAX_INCOMING_SYNCS_INC_DEFAULT_VALUE, settings.getMaxIncomingSyncsInc());
        Assertions.assertEquals(BUFFER_SIZE_DEFAULT_VALUE, settings.getBufferSize());
        Assertions.assertEquals(SOCKET_IP_TOS_DEFAULT_VALUE, settings.getSocketIpTos());
        Assertions.assertEquals(HALF_LIFE_DEFAULT_VALUE, settings.getHalfLife());
        Assertions.assertEquals(
                Integer.parseInt(ConsensusConfig.COIN_FREQ_DEFAULT_VALUE),
                configuration.getConfigData(ConsensusConfig.class).coinFreq());
        Assertions.assertEquals(LOG_STACK_DEFAULT_VALUE, settings.isLogStack());
        Assertions.assertEquals(USE_TLS_DEFAULT_VALUE, settings.isUseTLS());
        Assertions.assertEquals(DO_UPNP_DEFAULT_VALUE, settings.isDoUpnp());
        Assertions.assertEquals(USE_LOOPBACK_IP_DEFAULT_VALUE, settings.isUseLoopbackIp());
        Assertions.assertEquals(TCP_NO_DELAY_DEFAULT_VALUE, settings.isTcpNoDelay());
        Assertions.assertEquals(TIMEOUT_SYNC_CLIENT_SOCKET_DEFAULT_VALUE, settings.getTimeoutSyncClientSocket());
        Assertions.assertEquals(TIMEOUT_SYNC_CLIENT_CONNECT_DEFAULT_VALUE, settings.getTimeoutSyncClientConnect());
        Assertions.assertEquals(TIMEOUT_SERVER_ACCEPT_CONNECT_DEFAULT_VALUE, settings.getTimeoutServerAcceptConnect());
        Assertions.assertEquals(DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE, settings.getDeadlockCheckPeriod());
        Assertions.assertEquals(SLEEP_HEARTBEAT_DEFAULT_VALUE, settings.getSleepHeartbeat());
        Assertions.assertEquals(DELAY_SHUFFLE_DEFAULT_VALUE, settings.getDelayShuffle());
        Assertions.assertEquals(CALLER_SKIPS_BEFORE_SLEEP_DEFAULT_VALUE, settings.getCallerSkipsBeforeSleep());
        Assertions.assertEquals(SLEEP_CALLER_SKIPS_DEFAULT_VALUE, settings.getSleepCallerSkips());
        Assertions.assertEquals(STATS_SKIP_SECONDS_DEFAULT_VALUE, settings.getStatsSkipSeconds());
        Assertions.assertEquals(THREAD_PRIORITY_SYNC_DEFAULT_VALUE, settings.getThreadPrioritySync());
        Assertions.assertEquals(THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE, settings.getThreadPriorityNonSync());
        Assertions.assertEquals(TRANSACTION_MAX_BYTES_DEFAULT_VALUES, settings.getTransactionMaxBytes());
        Assertions.assertEquals(MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE, settings.getMaxAddressSizeAllowed());
        Assertions.assertEquals(FREEZE_SECONDS_AFTER_STARTUP_DEFAULT_VALUE, settings.getFreezeSecondsAfterStartup());
        Assertions.assertEquals(LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE, settings.isLoadKeysFromPfxFiles());
        Assertions.assertEquals(
                MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE, settings.getMaxTransactionBytesPerEvent());
        Assertions.assertEquals(
                MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE, settings.getMaxTransactionCountPerEvent());
        Assertions.assertEquals(TRANS_THROTTLE_DEFAULT_VALUE, settings.isTransThrottle());
        Assertions.assertEquals(CSV_OUTPUT_FOLDER_DEFAULT_VALUE, settings.getCsvOutputFolder());
        Assertions.assertEquals(CSV_FILE_NAME_DEFAULT_VALUE, settings.getCsvFileName());
        Assertions.assertEquals(CSV_WRITE_FREQUENCY_DEFAULT_VALUE, settings.getCsvWriteFrequency());
        Assertions.assertEquals(CSV_APPEND_DEFAULT_VALUE, settings.isCsvAppend());
        Assertions.assertEquals(
                EVENT_INTAKE_QUEUE_THROTTLE_SIZE_DEFAULT_VALUE, settings.getEventIntakeQueueThrottleSize());
        Assertions.assertEquals(EVENT_INTAKE_QUEUE_SIZE_DEFAULT_VALUE, settings.getEventIntakeQueueSize());
        Assertions.assertEquals(CHECK_SIGNED_STATE_FROM_DISK_DEFAULT_VALUE, settings.isCheckSignedStateFromDisk());
        Assertions.assertEquals(RANDOM_EVENT_PROBABILITY_DEFAULT_VALUE, settings.getRandomEventProbability());
        Assertions.assertEquals(
                STALE_EVENT_PREVENTION_THRESHOLD_DEFAULT_VALUE, settings.getStaleEventPreventionThreshold());
        Assertions.assertEquals(
                RESCUE_CHILDLESS_INVERSE_PROBABILITY_DEFAULT_VALUE, settings.getRescueChildlessInverseProbability());
        Assertions.assertEquals(RUN_PAUSE_CHECK_TIMER_DEFAULT_VALUE, settings.isRunPauseCheckTimer());
        Assertions.assertEquals(ENABLE_EVENT_STREAMING_DEFAULT_VALUE, settings.isEnableEventStreaming());
        Assertions.assertEquals(EVENT_STREAM_QUEUE_CAPACITY_DEFAULT_VALUE, settings.getEventStreamQueueCapacity());
        Assertions.assertEquals(EVENTS_LOG_PERIOD_DEFAULT_VALUE, settings.getEventsLogPeriod());
        Assertions.assertEquals(EVENTS_LOG_DIR_DEFAULT_VALUE, settings.getEventsLogDir());
        Assertions.assertEquals(THREAD_DUMP_PERIOD_MS_DEFAULT_VALUE, settings.getThreadDumpPeriodMs());
        Assertions.assertEquals(THREAD_DUMP_LOG_DIR_DEFAULT_VALUE, settings.getThreadDumpLogDir());
        Assertions.assertEquals(JVM_PAUSE_DETECTOR_SLEEP_MS_DEFAULT_VALUE, settings.getJVMPauseDetectorSleepMs());
        Assertions.assertEquals(JVM_PAUSE_REPORT_MS_DEFAULT_VALUE, settings.getJVMPauseReportMs());
        Assertions.assertEquals(GOSSIP_WITH_DIFFERENT_VERSIONS_DEFAULT_VALUE, settings.isGossipWithDifferentVersions());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded settings are retrieved correctly")
    public void checkGetLoadedSettings() throws IOException {
        // given
        final Settings settings = Settings.getInstance();
        final Path configPath = FileUtils.getAbsolutePath(CONFIG_TXT);
        final Path settingsPath = FileUtils.getAbsolutePath(SETTINGS_TXT);
        final Path keysDirectoryPath =
                FileUtils.getAbsolutePath().resolve(DATA_STRING).resolve(KEYS_STRING);
        final Path appsDirectoryPath =
                FileUtils.getAbsolutePath().resolve(DATA_STRING).resolve(APPS_STRING);
        final Path logPath = FileUtils.rethrowIO(() -> FileUtils.getAbsolutePath(LOG4J2_CONFIG_FILE));
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings4.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new LegacyFileConfigSource(settingsFile.toPath()))
                .getOrCreateConfig();

        // when
        settings.loadSettings(settingsFile);

        // then
        // These values shouldn't change as they are final
        Assertions.assertEquals(configPath, settings.getConfigPath());
        Assertions.assertEquals(settingsPath, settings.getSettingsPath());
        Assertions.assertEquals(keysDirectoryPath, settings.getKeysDirPath());
        Assertions.assertEquals(appsDirectoryPath, settings.getAppsDirPath());
        Assertions.assertEquals(logPath, settings.getLogPath());
        Assertions.assertEquals(THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE, settings.getThreadPriorityNonSync());

        // These values should change
        Assertions.assertFalse(settings.isVerifyEventSigs());
        Assertions.assertEquals(16, settings.getNumCryptoThreads());
        Assertions.assertTrue(settings.isShowInternalStats());
        Assertions.assertTrue(settings.isVerboseStatistics());
        Assertions.assertTrue(settings.isRequireStateLoad());
        Assertions.assertEquals(3, settings.getSignedStateFreq());
        Assertions.assertEquals(600, settings.getMaxEventQueueForCons());
        Assertions.assertEquals(200000, settings.getThrottleTransactionQueueSize());
        Assertions.assertEquals(50, settings.getNumConnections());
        Assertions.assertEquals(3, settings.getMaxOutgoingSyncs());
        Assertions.assertEquals(2, settings.getMaxIncomingSyncsInc());
        Assertions.assertEquals(7000, settings.getBufferSize());
        Assertions.assertEquals(1, settings.getSocketIpTos());
        Assertions.assertEquals(5, settings.getHalfLife());
        Assertions.assertFalse(settings.isLogStack());
        Assertions.assertFalse(settings.isUseTLS());
        Assertions.assertFalse(settings.isDoUpnp());
        Assertions.assertFalse(settings.isUseLoopbackIp());
        Assertions.assertFalse(settings.isTcpNoDelay());
        Assertions.assertEquals(1000, settings.getTimeoutSyncClientSocket());
        Assertions.assertEquals(1000, settings.getTimeoutSyncClientConnect());
        Assertions.assertEquals(1000, settings.getTimeoutServerAcceptConnect());
        Assertions.assertEquals(2000, settings.getDeadlockCheckPeriod());
        Assertions.assertEquals(1000, settings.getSleepHeartbeat());
        Assertions.assertEquals(300, settings.getDelayShuffle());
        Assertions.assertEquals(50, settings.getCallerSkipsBeforeSleep());
        Assertions.assertEquals(100, settings.getSleepCallerSkips());
        Assertions.assertEquals(70, settings.getStatsSkipSeconds());
        Assertions.assertEquals(10, settings.getThreadPrioritySync());
        Assertions.assertEquals(7000, settings.getTransactionMaxBytes());
        Assertions.assertEquals(2048, settings.getMaxAddressSizeAllowed());
        Assertions.assertEquals(15, settings.getFreezeSecondsAfterStartup());
        Assertions.assertFalse(settings.isLoadKeysFromPfxFiles());
        Assertions.assertEquals(300000, settings.getMaxTransactionBytesPerEvent());
        Assertions.assertEquals(300000, settings.getMaxTransactionCountPerEvent());
        Assertions.assertFalse(settings.isTransThrottle());
        Assertions.assertEquals("csvFolder", settings.getCsvOutputFolder());
        Assertions.assertEquals("csvFile", settings.getCsvFileName());
        Assertions.assertEquals(4000, settings.getCsvWriteFrequency());
        Assertions.assertTrue(settings.isCsvAppend());
        Assertions.assertEquals(2000, settings.getEventIntakeQueueThrottleSize());
        Assertions.assertEquals(15000, settings.getEventIntakeQueueSize());
        Assertions.assertTrue(settings.isCheckSignedStateFromDisk());
        Assertions.assertEquals(1, settings.getRandomEventProbability());
        Assertions.assertEquals(10, settings.getStaleEventPreventionThreshold());
        Assertions.assertEquals(15, settings.getRescueChildlessInverseProbability());
        Assertions.assertTrue(settings.isRunPauseCheckTimer());
        Assertions.assertTrue(settings.isEnableEventStreaming());
        Assertions.assertEquals(1000, settings.getEventStreamQueueCapacity());
        Assertions.assertEquals(70, settings.getEventsLogPeriod());
        Assertions.assertEquals("badEventsStream", settings.getEventsLogDir());
        Assertions.assertEquals(1, settings.getThreadDumpPeriodMs());
        Assertions.assertEquals("badData/badThreadDump", settings.getThreadDumpLogDir());
        Assertions.assertEquals(2000, settings.getJVMPauseDetectorSleepMs());
        Assertions.assertEquals(2000, settings.getJVMPauseReportMs());
        Assertions.assertTrue(settings.isGossipWithDifferentVersions());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default state sub-settings are retrieved correctly")
    public void checkGetDefaultStateSubSettings() {
        // given
        final StateSettings stateSettings = Settings.getInstance().getState();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        // then
        Assertions.assertEquals("data/saved", stateSettings.savedStateDirectory);
        Assertions.assertFalse(stateSettings.cleanSavedStateDirectory);
        Assertions.assertEquals(20, stateSettings.stateSavingQueueSize);
        Assertions.assertEquals(0, stateSettings.getSaveStatePeriod());
        Assertions.assertEquals(3, stateSettings.getSignedStateDisk());
        Assertions.assertEquals(
                Integer.parseInt(ConsensusConfig.ROUNDS_EXPIRED_DEFAULT_VALUE),
                configuration.getConfigData(ConsensusConfig.class).roundsExpired());
        Assertions.assertEquals(
                Integer.parseInt(ConsensusConfig.ROUNDS_NON_ANCIENT_DEFAULT_VALUE),
                configuration.getConfigData(ConsensusConfig.class).roundsNonAncient());
        Assertions.assertTrue(stateSettings.dumpStateOnFatal);
        Assertions.assertEquals(Duration.ofHours(6).toSeconds(), stateSettings.secondsBetweenISSDumps);
        Assertions.assertFalse(StateSettings.backgroundHashChecking);
        Assertions.assertEquals(5, StateSettings.getDebugHashDepth());
        Assertions.assertEquals(60, stateSettings.getStateDeletionErrorLogFrequencySeconds());
        Assertions.assertTrue(stateSettings.enableHashStreamLogging);
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded state sub-settings are retrieved correctly")
    public void checkGetLoadedStateSubSettings() throws IOException {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings5.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new LegacyFileConfigSource(settingsFile.toPath()))
                .getOrCreateConfig();

        // when
        settings.loadSettings(settingsFile);
        final StateSettings stateSettings = Settings.getInstance().getState();

        // then
        Assertions.assertEquals("badData/badSaved", stateSettings.savedStateDirectory);
        Assertions.assertTrue(stateSettings.cleanSavedStateDirectory);
        Assertions.assertEquals(30, stateSettings.stateSavingQueueSize);
        Assertions.assertEquals(1, stateSettings.getSaveStatePeriod());
        Assertions.assertEquals(4, stateSettings.getSignedStateDisk());
        Assertions.assertEquals(
                1000, configuration.getConfigData(ConsensusConfig.class).roundsExpired());
        Assertions.assertEquals(
                30, configuration.getConfigData(ConsensusConfig.class).roundsNonAncient());
        Assertions.assertFalse(stateSettings.dumpStateOnFatal);
        Assertions.assertEquals(6000, stateSettings.secondsBetweenISSDumps);
        Assertions.assertTrue(StateSettings.backgroundHashChecking);
        Assertions.assertEquals(10, StateSettings.getDebugHashDepth());
        Assertions.assertEquals(120, stateSettings.getStateDeletionErrorLogFrequencySeconds());
        Assertions.assertFalse(stateSettings.enableHashStreamLogging);
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default crypto sub-settings are retrieved correctly")
    public void checkGetDefaultCryptoSubSettings() {
        // given
        final CryptoConfig cryptoConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(CryptoConfig.class);

        // then
        Assertions.assertEquals(0.5, cryptoConfig.cpuVerifierThreadRatio());
        Assertions.assertEquals(0.5, cryptoConfig.cpuDigestThreadRatio());
        Assertions.assertEquals(100, cryptoConfig.cpuVerifierQueueSize());
        Assertions.assertEquals(100, cryptoConfig.cpuDigestQueueSize());
        Assertions.assertTrue(cryptoConfig.forceCpu());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded crypto sub-settings are retrieved correctly")
    public void checkGetLoadedCryptoSubSettings() throws IOException {
        // given
        final CryptoConfig cryptoConfig = new TestConfigBuilder()
                .withSource(new LegacyFileConfigSource(
                        SettingsTest.class.getResource("settings6.txt").getFile()))
                .getOrCreateConfig()
                .getConfigData(CryptoConfig.class);

        // then
        Assertions.assertEquals(0.75, cryptoConfig.cpuVerifierThreadRatio());
        Assertions.assertEquals(0.25, cryptoConfig.cpuDigestThreadRatio());
        Assertions.assertEquals(150, cryptoConfig.cpuVerifierQueueSize());
        Assertions.assertEquals(150, cryptoConfig.cpuDigestQueueSize());
        Assertions.assertFalse(cryptoConfig.forceCpu());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default reconnect sub-settings are retrieved correctly")
    public void checkGetDefaultReconnectSubSettings() {
        // given
        final ReconnectSettingsImpl reconnectSettings = Settings.getInstance().getReconnect();

        // then
        Assertions.assertFalse(reconnectSettings.isActive());
        Assertions.assertEquals(-1, reconnectSettings.getReconnectWindowSeconds());
        Assertions.assertEquals(0.5, reconnectSettings.getFallenBehindThreshold());
        Assertions.assertEquals(100000, reconnectSettings.getAsyncStreamTimeoutMilliseconds());
        Assertions.assertEquals(100, reconnectSettings.getAsyncOutputStreamFlushMilliseconds());
        Assertions.assertEquals(10000, reconnectSettings.getAsyncStreamBufferSize());
        Assertions.assertTrue(reconnectSettings.asyncStreams);
        Assertions.assertEquals(10, reconnectSettings.getMaxAckDelayMilliseconds());
        Assertions.assertEquals(10, reconnectSettings.getMaximumReconnectFailuresBeforeShutdown());
        Assertions.assertEquals(Duration.ofMinutes(10), reconnectSettings.getMinimumTimeBetweenReconnects());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded reconnect sub-settings are retrieved correctly")
    public void checkGetLoadedReconnectSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings7.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.loadSettings(settingsFile);
        final ReconnectSettingsImpl reconnectSettings = Settings.getInstance().getReconnect();

        // then
        Assertions.assertTrue(reconnectSettings.isActive());
        Assertions.assertEquals(1, reconnectSettings.getReconnectWindowSeconds());
        Assertions.assertEquals(0.75, reconnectSettings.getFallenBehindThreshold());
        Assertions.assertEquals(200000, reconnectSettings.getAsyncStreamTimeoutMilliseconds());
        Assertions.assertEquals(150, reconnectSettings.getAsyncOutputStreamFlushMilliseconds());
        Assertions.assertEquals(15000, reconnectSettings.getAsyncStreamBufferSize());
        Assertions.assertFalse(reconnectSettings.asyncStreams);
        Assertions.assertEquals(20, reconnectSettings.getMaxAckDelayMilliseconds());
        Assertions.assertEquals(15, reconnectSettings.getMaximumReconnectFailuresBeforeShutdown());
        Assertions.assertEquals(
                ParsingUtils.parseDuration("11min"), reconnectSettings.getMinimumTimeBetweenReconnects());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default FCHashMap sub-settings are retrieved correctly")
    public void checkGetDefaultFCHashMapSubSettings() {
        // given
        final FCHashMapSettingsImpl fcHashMapSettings = Settings.getInstance().getFcHashMap();

        // then
        Assertions.assertEquals(200, fcHashMapSettings.getMaximumGCQueueSize());
        Assertions.assertEquals(Duration.ofMinutes(1), fcHashMapSettings.getGCQueueThresholdPeriod());
        Assertions.assertTrue(fcHashMapSettings.isArchiveEnabled());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded FCHashMap sub-settings are retrieved correctly")
    public void checkGetLoadedFCHashMapSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings8.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.loadSettings(settingsFile);
        final FCHashMapSettingsImpl fcHashMapSettings = Settings.getInstance().getFcHashMap();

        // then
        Assertions.assertEquals(250, fcHashMapSettings.getMaximumGCQueueSize());
        Assertions.assertEquals(ParsingUtils.parseDuration("2min"), fcHashMapSettings.getGCQueueThresholdPeriod());
        Assertions.assertFalse(fcHashMapSettings.isArchiveEnabled());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default virtual map sub-settings are retrieved correctly")
    public void checkGetDefaultVirtualMapSubSettings() {
        // given
        final VirtualMapSettingsImpl virtualMapSettings = Settings.getInstance().getVirtualMap();
        final int numProcessors = Runtime.getRuntime().availableProcessors();

        // then
        Assertions.assertEquals(DEFAULT_PERCENT_HASH_THREADS, virtualMapSettings.getPercentHashThreads());
        Assertions.assertEquals(
                (int) (numProcessors * (virtualMapSettings.getPercentHashThreads() / UNIT_FRACTION_PERCENT)),
                virtualMapSettings.getNumHashThreads());
        Assertions.assertEquals(DEFAULT_PERCENT_CLEANER_THREADS, virtualMapSettings.getPercentCleanerThreads());
        Assertions.assertEquals(
                (int) (numProcessors * (virtualMapSettings.getPercentCleanerThreads() / UNIT_FRACTION_PERCENT)),
                virtualMapSettings.getNumCleanerThreads());
        Assertions.assertEquals(DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE, virtualMapSettings.getMaximumVirtualMapSize());
        Assertions.assertEquals(
                DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD, virtualMapSettings.getVirtualMapWarningThreshold());
        Assertions.assertEquals(
                DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL, virtualMapSettings.getVirtualMapWarningInterval());
        Assertions.assertEquals(DEFAULT_FLUSH_INTERVAL, virtualMapSettings.getFlushInterval());
        Assertions.assertEquals(DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE, virtualMapSettings.getPreferredFlushQueueSize());
        Assertions.assertEquals(DEFAULT_FLUSH_THROTTLE_STEP_SIZE, virtualMapSettings.getFlushThrottleStepSize());
        Assertions.assertEquals(
                DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD, virtualMapSettings.getMaximumFlushThrottlePeriod());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded virtual map sub-settings are retrieved correctly")
    public void checkGetLoadedVirtualMapSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings9.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.loadSettings(settingsFile);
        final VirtualMapSettingsImpl virtualMapSettings = Settings.getInstance().getVirtualMap();

        // then
        Assertions.assertEquals(1, virtualMapSettings.getNumHashThreads());
        Assertions.assertEquals(75.0, virtualMapSettings.getPercentHashThreads());
        Assertions.assertEquals(1, virtualMapSettings.getNumCleanerThreads());
        Assertions.assertEquals(50.0, virtualMapSettings.getPercentCleanerThreads());
        Assertions.assertEquals(10, virtualMapSettings.getMaximumVirtualMapSize());
        Assertions.assertEquals(7500000, virtualMapSettings.getVirtualMapWarningThreshold());
        Assertions.assertEquals(150000, virtualMapSettings.getVirtualMapWarningInterval());
        Assertions.assertEquals(30, virtualMapSettings.getFlushInterval());
        Assertions.assertEquals(3, virtualMapSettings.getPreferredFlushQueueSize());
        Assertions.assertEquals(ParsingUtils.parseDuration("300millis"), virtualMapSettings.getFlushThrottleStepSize());
        Assertions.assertEquals(
                ParsingUtils.parseDuration("6secs"), virtualMapSettings.getMaximumFlushThrottlePeriod());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default address book sub-settings are retrieved correctly")
    public void checkGetDefaultAddressBookSubSettings() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        // when
        final AddressBookConfig addressBookConfig = configuration.getConfigData(AddressBookConfig.class);

        // then
        Assertions.assertTrue(addressBookConfig.updateAddressBookOnlyAtUpgrade());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded address book sub-settings are retrieved correctly")
    public void checkGetLoadedAddressBookSubSettings() throws IOException {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new LegacyFileConfigSource(
                        SettingsTest.class.getResource("settings10.txt").getFile()))
                .getOrCreateConfig();

        // when
        final AddressBookConfig addressBookConfig = configuration.getConfigData(AddressBookConfig.class);

        // then
        Assertions.assertFalse(addressBookConfig.updateAddressBookOnlyAtUpgrade());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default JasperDB sub-settings are retrieved correctly")
    public void checkGetDefaultJasperDbSubSettings() {
        // given
        final JasperDbSettingsImpl jasperDbSettings = Settings.getInstance().getJasperDb();

        // then
        Assertions.assertEquals(DEFAULT_MAX_NUM_OF_KEYS, jasperDbSettings.getMaxNumOfKeys());
        Assertions.assertEquals(
                DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD, jasperDbSettings.getInternalHashesRamToDiskThreshold());
        Assertions.assertEquals(DEFAULT_SMALL_MERGE_CUTOFF_MB, jasperDbSettings.getSmallMergeCutoffMb());
        Assertions.assertEquals(DEFAULT_MEDIUM_MERGE_CUTOFF_MB, jasperDbSettings.getMediumMergeCutoffMb());
        Assertions.assertEquals(DEFAULT_MOVE_LIST_CHUNK_SIZE, jasperDbSettings.getMoveListChunkSize());
        Assertions.assertEquals(DEFAULT_MAX_GB_RAM_FOR_MERGING, jasperDbSettings.getMaxRamUsedForMergingGb());
        Assertions.assertEquals(DEFAULT_ITERATOR_INPUT_BUFFER_BYTES, jasperDbSettings.getIteratorInputBufferBytes());
        Assertions.assertEquals(DEFAULT_WRITER_OUTPUT_BUFFER_BYTES, jasperDbSettings.getWriterOutputBufferBytes());
        Assertions.assertEquals(DEFAULT_MAX_FILE_SIZE_BYTES, jasperDbSettings.getMaxDataFileBytes());
        Assertions.assertEquals(DEFAULT_FULL_MERGE_PERIOD, jasperDbSettings.getFullMergePeriod());
        Assertions.assertEquals(DEFAULT_MEDIUM_MERGE_PERIOD, jasperDbSettings.getMediumMergePeriod());
        Assertions.assertEquals(DEFAULT_MERGE_ACTIVATED_PERIOD, jasperDbSettings.getMergeActivatePeriod());
        Assertions.assertEquals(DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE, jasperDbSettings.getMaxNumberOfFilesInMerge());
        Assertions.assertEquals(DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE, jasperDbSettings.getMinNumberOfFilesInMerge());
        Assertions.assertEquals(
                DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED, jasperDbSettings.isReconnectKeyLeakMitigationEnabled());
        Assertions.assertEquals(ChronoUnit.valueOf("MINUTES"), jasperDbSettings.getMergePeriodUnit());
        Assertions.assertEquals(
                DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT, jasperDbSettings.getKeySetBloomFilterHashCount());
        Assertions.assertEquals(
                DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES, jasperDbSettings.getKeySetBloomFilterSizeInBytes());
        Assertions.assertEquals(
                DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE, jasperDbSettings.getKeySetHalfDiskHashMapSize());
        Assertions.assertEquals(
                DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER, jasperDbSettings.getKeySetHalfDiskHashMapBuffer());
        Assertions.assertEquals(DEFAULT_INDEX_REBUILDING_ENFORCED, jasperDbSettings.isIndexRebuildingEnforced());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded JasperDB sub-settings are retrieved correctly")
    public void checkGetLoadedJasperDbSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings11.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.loadSettings(settingsFile);
        final JasperDbSettingsImpl jasperDbSettings = Settings.getInstance().getJasperDb();

        // then
        Assertions.assertEquals(250000000, jasperDbSettings.getMaxNumOfKeys());
        Assertions.assertEquals(1, jasperDbSettings.getInternalHashesRamToDiskThreshold());
        Assertions.assertEquals(4096, jasperDbSettings.getSmallMergeCutoffMb());
        Assertions.assertEquals(40960, jasperDbSettings.getMediumMergeCutoffMb());
        Assertions.assertEquals(250000, jasperDbSettings.getMoveListChunkSize());
        Assertions.assertEquals(15, jasperDbSettings.getMaxRamUsedForMergingGb());
        Assertions.assertEquals(500000000, jasperDbSettings.getIteratorInputBufferBytes());
        Assertions.assertEquals(500000000, jasperDbSettings.getWriterOutputBufferBytes());
        Assertions.assertEquals(500000000, jasperDbSettings.getMaxDataFileBytes());
        Assertions.assertEquals(2000, jasperDbSettings.getFullMergePeriod());
        Assertions.assertEquals(100, jasperDbSettings.getMediumMergePeriod());
        Assertions.assertEquals(2, jasperDbSettings.getMergeActivatePeriod());
        Assertions.assertEquals(128, jasperDbSettings.getMaxNumberOfFilesInMerge());
        Assertions.assertEquals(16, jasperDbSettings.getMinNumberOfFilesInMerge());
        Assertions.assertTrue(jasperDbSettings.isReconnectKeyLeakMitigationEnabled());
        // Assertions.assertEquals("Seconds", jasperDbSettings.getMergePeriodUnit());
        Assertions.assertEquals(15, jasperDbSettings.getKeySetBloomFilterHashCount());
        Assertions.assertEquals(300000000, jasperDbSettings.getKeySetBloomFilterSizeInBytes());
        Assertions.assertEquals(200000000, jasperDbSettings.getKeySetHalfDiskHashMapSize());
        Assertions.assertEquals(200000000, jasperDbSettings.getKeySetHalfDiskHashMapBuffer());
        Assertions.assertTrue(jasperDbSettings.isIndexRebuildingEnforced());
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default temporary file sub-settings are retrieved correctly")
    public void checkGetDefaultTemporaryFileSubSettings() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        // when
        final TemporaryFileConfig temporaryFileConfig = configuration.getConfigData(TemporaryFileConfig.class);

        // then
        Assertions.assertEquals(
                "data/saved/swirlds-tmp",
                temporaryFileConfig.getTemporaryFilePath(configuration.getConfigData(StateConfig.class)));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded temporary file sub-settings are retrieved correctly")
    public void checkGetLoadedTemporaryFileSubSettings() throws IOException {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new LegacyFileConfigSource(
                        SettingsTest.class.getResource("settings12.txt").getFile()))
                .getOrCreateConfig();

        // when
        final TemporaryFileConfig temporaryFileConfig = configuration.getConfigData(TemporaryFileConfig.class);

        // then
        Assertions.assertEquals(
                Path.of("data", "saved", "badSwirldsTmp").toString(),
                temporaryFileConfig.getTemporaryFilePath(configuration.getConfigData(StateConfig.class)));
    }

    /**
     * Currently disabled until the Settings class gets rewritten to not use a singleton design pattern. There are tests
     * that are run that modify these default values before this test is run, therefore resulting in this test failing.
     */
    @Test
    @Disabled
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that default chatter sub-settings are retrieved correctly")
    public void checkGetDefaultChatterSubSettings() {
        // given
        final ChatterSubSetting chatterSubSetting = Settings.getInstance().getChatter();

        // then
        Assertions.assertFalse(chatterSubSetting.isChatterUsed());
        Assertions.assertEquals(50, chatterSubSetting.getAttemptedChatterEventPerSecond());
        Assertions.assertEquals(0.5, chatterSubSetting.getChatteringCreationThreshold());
        Assertions.assertEquals(20, chatterSubSetting.getChatterIntakeThrottle());
        Assertions.assertEquals(Duration.ofMillis(500), chatterSubSetting.getOtherEventDelay());
        Assertions.assertEquals(1500, chatterSubSetting.getSelfEventQueueCapacity());
        Assertions.assertEquals(45000, chatterSubSetting.getOtherEventQueueCapacity());
        Assertions.assertEquals(45000, chatterSubSetting.getDescriptorQueueCapacity());
        Assertions.assertEquals(Duration.ofMillis(100), chatterSubSetting.getProcessingTimeInterval());
        Assertions.assertEquals(Duration.ofSeconds(1), chatterSubSetting.getHeartbeatInterval());
        Assertions.assertEquals(100000, chatterSubSetting.getFutureGenerationLimit());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded chatter sub-settings are retrieved correctly")
    public void checkGetLoadedChatterSubSettings() {
        // given
        final Settings settings = Settings.getInstance();
        final File settingsFile =
                new File(SettingsTest.class.getResource("settings13.txt").getFile());
        Assertions.assertTrue(settingsFile.exists());

        // when
        settings.loadSettings(settingsFile);
        final ChatterSubSetting chatterSubSetting = Settings.getInstance().getChatter();

        // then
        Assertions.assertTrue(chatterSubSetting.isChatterUsed());
        Assertions.assertEquals(60, chatterSubSetting.getAttemptedChatterEventPerSecond());
        Assertions.assertEquals(0.75, chatterSubSetting.getChatteringCreationThreshold());
        Assertions.assertEquals(30, chatterSubSetting.getChatterIntakeThrottle());
        Assertions.assertEquals(ParsingUtils.parseDuration("600millis"), chatterSubSetting.getOtherEventDelay());
        Assertions.assertEquals(2000, chatterSubSetting.getSelfEventQueueCapacity());
        Assertions.assertEquals(50000, chatterSubSetting.getOtherEventQueueCapacity());
        Assertions.assertEquals(50000, chatterSubSetting.getDescriptorQueueCapacity());
        Assertions.assertEquals(ParsingUtils.parseDuration("200millis"), chatterSubSetting.getProcessingTimeInterval());
        Assertions.assertEquals(ParsingUtils.parseDuration("2secs"), chatterSubSetting.getHeartbeatInterval());
        Assertions.assertEquals(150000, chatterSubSetting.getFutureGenerationLimit());
    }

    private String readValueFromFile(final Path settingsPath, final String propertyName) throws IOException {
        try (final BufferedReader br = new BufferedReader(new FileReader(settingsPath.toFile()))) {
            return br.lines()
                    .filter(line -> line.endsWith(propertyName))
                    .findAny()
                    .map(line -> line.substring(0, line.length() - ("= " + propertyName).length()))
                    .map(line -> line.trim())
                    .orElseThrow(() -> new IllegalStateException(
                            "Property '" + propertyName + "' not found in saved settings file"));
        }
    }
}
