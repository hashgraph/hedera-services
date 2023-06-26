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

import static com.swirlds.platform.SettingConstants.DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.NUM_CRYPTO_THREADS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.SHOW_INTERNAL_STATS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_DUMP_LOG_DIR_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_DUMP_PERIOD_MS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THREAD_PRIORITY_SYNC_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.TRANSACTION_MAX_BYTES_DEFAULT_VALUES;
import static com.swirlds.platform.SettingConstants.VERBOSE_STATISTICS_DEFAULT_VALUE;
import static com.swirlds.platform.SettingConstants.VERIFY_EVENT_SIGS_DEFAULT_VALUE;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
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
    @DisplayName("Checks that loading settings with migrated settings does not throw an exception")
    public void checkOnlyConfigSettingsFile() {
        // given
        final Settings settings = Settings.getInstance();
        final File emptyFile =
                new File(SettingsTest.class.getResource("settings13.txt").getFile());

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
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        // then
        Assertions.assertEquals(VERIFY_EVENT_SIGS_DEFAULT_VALUE, settings.isVerifyEventSigs());
        Assertions.assertEquals(NUM_CRYPTO_THREADS_DEFAULT_VALUE, settings.getNumCryptoThreads());
        Assertions.assertEquals(SHOW_INTERNAL_STATS_DEFAULT_VALUE, settings.isShowInternalStats());
        Assertions.assertEquals(VERBOSE_STATISTICS_DEFAULT_VALUE, settings.isVerboseStatistics());
        Assertions.assertEquals(
                THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE, settings.getThrottleTransactionQueueSize());
        Assertions.assertEquals(
                Integer.parseInt(ConsensusConfig.COIN_FREQ_DEFAULT_VALUE),
                configuration.getConfigData(ConsensusConfig.class).coinFreq());
        Assertions.assertEquals(DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE, settings.getDeadlockCheckPeriod());
        Assertions.assertEquals(THREAD_PRIORITY_SYNC_DEFAULT_VALUE, settings.getThreadPrioritySync());
        Assertions.assertEquals(THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE, settings.getThreadPriorityNonSync());
        Assertions.assertEquals(TRANSACTION_MAX_BYTES_DEFAULT_VALUES, settings.getTransactionMaxBytes());
        Assertions.assertEquals(MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE, settings.getMaxAddressSizeAllowed());
        Assertions.assertEquals(LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE, settings.isLoadKeysFromPfxFiles());
        Assertions.assertEquals(
                MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE, settings.getMaxTransactionBytesPerEvent());
        Assertions.assertEquals(
                MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE, settings.getMaxTransactionCountPerEvent());
        Assertions.assertEquals(THREAD_DUMP_PERIOD_MS_DEFAULT_VALUE, settings.getThreadDumpPeriodMs());
        Assertions.assertEquals(THREAD_DUMP_LOG_DIR_DEFAULT_VALUE, settings.getThreadDumpLogDir());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Checks that loaded settings are retrieved correctly")
    public void checkGetLoadedSettings() throws IOException {
        // given
        final Settings settings = Settings.getInstance();
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
        Assertions.assertEquals(THREAD_PRIORITY_NON_SYNC_DEFAULT_VALUE, settings.getThreadPriorityNonSync());

        // These values should change
        Assertions.assertFalse(settings.isVerifyEventSigs());
        Assertions.assertEquals(16, settings.getNumCryptoThreads());
        Assertions.assertTrue(settings.isShowInternalStats());
        Assertions.assertTrue(settings.isVerboseStatistics());
        Assertions.assertEquals(200000, settings.getThrottleTransactionQueueSize());
        Assertions.assertEquals(2000, settings.getDeadlockCheckPeriod());
        Assertions.assertEquals(10, settings.getThreadPrioritySync());
        Assertions.assertEquals(7000, settings.getTransactionMaxBytes());
        Assertions.assertEquals(2048, settings.getMaxAddressSizeAllowed());
        Assertions.assertFalse(settings.isLoadKeysFromPfxFiles());
        Assertions.assertEquals(300000, settings.getMaxTransactionBytesPerEvent());
        Assertions.assertEquals(300000, settings.getMaxTransactionCountPerEvent());
        Assertions.assertEquals(1, settings.getThreadDumpPeriodMs());
        Assertions.assertEquals("badData/badThreadDump", settings.getThreadDumpLogDir());
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

    private String readValueFromFile(Path settingsPath, String propertyName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(settingsPath.toFile()))) {
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
