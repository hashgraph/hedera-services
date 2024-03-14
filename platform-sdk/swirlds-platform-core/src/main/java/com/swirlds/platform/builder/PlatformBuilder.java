/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.builder;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds a {@link SwirldsPlatform} instance.
 */
public final class PlatformBuilder {

    private boolean used = false;

    private final String appName;
    private final SoftwareVersion softwareVersion;
    private final Supplier<SwirldState> genesisStateBuilder;
    private final NodeId selfId;
    private final String swirldName;

    private PlatformFactory platformComponents;
    private ConfigurationBuilder configurationBuilder;

    private static final String SWIRLDS_PACKAGE = "com.swirlds";

    public static final String DEFAULT_CONFIG_FILE_NAME = "config.txt";
    public static final String DEFAULT_SETTINGS_FILE_NAME = "settings.txt";

    /**
     * The path to the configuration file (i.e. the file with the address book).
     */
    private Path configPath = getAbsolutePath(DEFAULT_CONFIG_FILE_NAME);

    /**
     * The path to the settings file (i.e. the path used to instantiate {@link Configuration}).
     */
    private Path settingsPath = getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME);

    /**
     * Create a new platform builder.
     *
     * @param appName             the name of the application, currently used for deciding where to store states on
     *                            disk
     * @param swirldName          the name of the swirld, currently used for deciding where to store states on disk
     * @param selfId              the ID of this node
     * @param softwareVersion     the software version of the application
     * @param genesisStateBuilder a supplier that will be called to create the genesis state, if necessary
     */
    public PlatformBuilder(
            @NonNull final String appName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final NodeId selfId) {

        this.appName = Objects.requireNonNull(appName);
        this.swirldName = Objects.requireNonNull(swirldName);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.genesisStateBuilder = Objects.requireNonNull(genesisStateBuilder);
        this.selfId = Objects.requireNonNull(selfId);

        StaticSoftwareVersion.setSoftwareVersion(softwareVersion);
    }

    /**
     * Get the ID of this node.
     *
     * @return the ID of this node
     */
    @NonNull
    NodeId getSelfId() {
        return selfId;
    }

    /**
     * Get the application's current software version.
     *
     * @return the software version
     */
    @NonNull
    SoftwareVersion getAppVersion() {
        return softwareVersion;
    }

    /**
     * Get the name of the application.
     *
     * @return the name of the application
     */
    @NonNull
    String getAppName() {
        return appName;
    }

    /**
     * Get the name of the swirld.
     *
     * @return the name of the swirld
     */
    @NonNull
    String getSwirldName() {
        return swirldName;
    }

    /**
     * Get the supplier that will be called to create the genesis state, if necessary.
     *
     * @return the supplier that will be called to create the genesis state
     */
    @NonNull
    Supplier<SwirldState> getGenesisStateBuilder() {
        return genesisStateBuilder;
    }

    /**
     * Set the configuration builder to use. If not provided then one is generated when the platform is built.
     *
     * @param configurationBuilder the configuration builder to use
     * @return this
     */
    @NonNull
    public PlatformBuilder withConfigurationBuilder(@Nullable final ConfigurationBuilder configurationBuilder) {
        throwIfUsed();
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    /**
     * Set the path to the settings file (i.e. the file used to instantiate {@link Configuration}). Traditionally named
     * {@link #DEFAULT_SETTINGS_FILE_NAME}.
     *
     * @param path the path to the settings file
     * @return this
     */
    @NonNull
    public PlatformBuilder withSettingsPath(@NonNull final Path path) {
        throwIfUsed();
        Objects.requireNonNull(path);
        this.settingsPath = getAbsolutePath(path);
        return this;
    }

    /**
     * The path to the config file (i.e. the file with the address book. Traditionally named
     * {@link #DEFAULT_CONFIG_FILE_NAME}.
     *
     * @param path the path to the config file
     * @return this
     */
    @NonNull
    public PlatformBuilder withConfigPath(@NonNull final Path path) {
        throwIfUsed();
        Objects.requireNonNull(path);
        this.configPath = getAbsolutePath(path);
        return this;
    }

    /**
     * The path to the config file (i.e. the file with the address book. Traditionally named
     * {@link #DEFAULT_CONFIG_FILE_NAME}.
     *
     * @return the path to the config file
     */
    @NonNull
    Path getConfigPath() {
        return configPath;
    }

    /**
     * Provide the platform with the class ID of the previous software version. Needed at migration boundaries if the
     * class ID of the software version has changed.
     *
     * @param previousSoftwareVersionClassId the class ID of the previous software version
     * @return this
     */
    @NonNull
    public PlatformBuilder withPreviousSoftwareVersionClassId(final long previousSoftwareVersionClassId) {
        throwIfUsed();
        final Set<Long> softwareVersions = new HashSet<>();
        softwareVersions.add(softwareVersion.getClassId());
        softwareVersions.add(previousSoftwareVersionClassId);
        StaticSoftwareVersion.setSoftwareVersion(softwareVersions);
        return this;
    }

    /**
     * Build the configuration for the node.
     *
     * @return the configuration
     */
    @NonNull
    Configuration buildConfiguration() { // TODO move this to platform factory
        if (configurationBuilder == null) {
            configurationBuilder = ConfigurationBuilder.create();
        }

        ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_PACKAGE));
        rethrowIO(() -> BootstrapUtils.setupConfigBuilder(configurationBuilder, settingsPath));

        final Configuration configuration = configurationBuilder.build();
        PlatformConfigUtils.checkConfiguration(configuration);

        return configuration;
    }

    /**
     * Parse the address book from the config.txt file.
     *
     * @return the address book
     */
    @NonNull
    AddressBook loadConfigAddressBook() { // TODO move to factory
        final LegacyConfigProperties legacyConfig = LegacyConfigPropertiesLoader.loadConfigFile(configPath);
        legacyConfig.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));
        return legacyConfig.getAddressBook();
    }

    /**
     * Build a platform. Platform is not started.
     *
     * <p>
     * Once this method has been called, all future calls to methods on this {@link PlatformBuilder} will throw an
     * {@link IllegalStateException}. Once the platform builder has been used to build a platform, it is considered
     * "used" and cannot be used again.
     *
     * @return a new platform instance
     */
    @NonNull
    public Platform build() {
        throwIfUsed();
        return buildPlatformFactory().build();
    }

    /**
     * Build a platform factory. The platform factory is responsible for building internal platform components. It is
     * possible to configure the platform factory in a way that overrides default platform components with custom
     * implementations.
     *
     * <p>
     * Once this method has been called, all future calls to methods on this {@link PlatformBuilder} will throw an
     * {@link IllegalStateException}. Once the platform builder has been used to assemble a component builder, it is
     * considered "used" and cannot be used again.
     *
     * <p>
     * This method is designed for use in advanced workflows where various components of the platform need to be swapped
     * out with custom implementations. If such advanced use cases are not required, then it is recommended to ignore
     * this method and to build the platform using the {@link #build()} method.
     *
     * @return the component builder
     */
    @NonNull
    public PlatformFactory buildPlatformFactory() {
        throwIfUsed();
        used = true;
        return new PlatformFactory(this);
    }

    /**
     * Throw an exception if this builder has been used to build a platform or a platform factory.
     */
    private void throwIfUsed() {
        if (used) {
            throw new IllegalStateException("PlatformBuilder has already been used");
        }
    }
}
