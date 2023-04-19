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

package com.swirlds.platform.util;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.Log4jSetup;
import com.swirlds.platform.Settings;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods that are helpful when starting up a JVM.
 */
public final class BootstrapUtils {

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(BootstrapUtils.class);

    private BootstrapUtils() {}

    /**
     * Start log4j.
     */
    public static void startLoggingFramework(final Path log4jPath) {
        if (log4jPath != null && Files.exists(log4jPath)) {
            Log4jSetup.startLoggingFramework(log4jPath);
        } else {
            Log4jSetup.startLoggingFramework(Settings.getInstance().getLogPath());
        }
    }

    /**
     * Add all classes to the constructable registry.
     */
    public static void setupConstructableRegistry() {
        try {
            ConstructableRegistry.getInstance().registerConstructables("");
        } catch (final ConstructableRegistryException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load the SwirldMain for the app.
     */
    public static SwirldMain loadAppMain(final String appMainName) {
        try {
            final Class<?> mainClass = Class.forName(appMainName);
            final Constructor<?>[] constructors = mainClass.getDeclaredConstructors();
            Constructor<?> constructor = null;
            for (Constructor<?> c : constructors) {
                if (c.getGenericParameterTypes().length == 0) {
                    constructor = c;
                    break;
                }
            }

            if (constructor == null) {
                throw new RuntimeException("Class " + appMainName + " does not have a zero arg constructor.");
            }

            return (SwirldMain) constructor.newInstance();
        } catch (final ClassNotFoundException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load configuration.
     */
    public static Configuration loadConfiguration(final List<Path> configurationPaths) throws IOException {
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();

        ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, "com.swirlds");

        for (final Path configPath : configurationPaths) {
            configurationBuilder.withSource(new LegacyFileConfigSource(configPath));
        }

        final Configuration configuration = configurationBuilder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        return configuration;
    }

    /**
     * Detect if there is a software upgrade. This method will throw an IllegalStateException if the loaded signed state
     * software version is greater than the current application software version.
     *
     * @param appVersion        the version of the app
     * @param loadedSignedState the signed state that was loaded from disk
     * @return true if there is a software upgrade, false otherwise
     */
    public static boolean detectSoftwareUpgrade(
            @NonNull final SoftwareVersion appVersion, @Nullable final SignedState loadedSignedState) {
        Objects.requireNonNull(appVersion, "The app version must not be null.");

        final SoftwareVersion loadedSoftwareVersion = loadedSignedState == null
                ? null
                : loadedSignedState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getCreationSoftwareVersion();
        final int versionComparison = loadedSoftwareVersion == null ? 1 : appVersion.compareTo(loadedSoftwareVersion);
        final boolean softwareUpgrade;
        if (versionComparison < 0) {
            throw new IllegalStateException(
                    "The current software version `" + appVersion + "` is prior to the software version `"
                            + loadedSoftwareVersion + "` that created the state that was loaded from disk.");
        } else if (versionComparison > 0) {
            softwareUpgrade = true;
            logger.info(
                    STARTUP.getMarker(),
                    "Software upgrade in progress. Previous software version was {}, current version is {}.",
                    loadedSoftwareVersion, appVersion);
        } else {
            softwareUpgrade = false;
            logger.info(
                    STARTUP.getMarker(),
                    "Not upgrading software, current software is version {}.",
                    loadedSoftwareVersion);
        }
        return softwareUpgrade;
    }
}
