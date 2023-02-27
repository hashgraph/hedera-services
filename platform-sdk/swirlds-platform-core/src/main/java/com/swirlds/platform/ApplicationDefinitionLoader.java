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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.config.legacy.AddressConfig;
import com.swirlds.platform.config.legacy.JarAppConfig;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class only contains one method that was extracted from the {@link Browser} class.
 * The method uses the {@link Settings} class in some special ways and will be replaced in future by the
 * {@link com.swirlds.config.api.Configuration} API.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future once the
 * 		config.txt has been migrated to the regular config API. If you need to use this class please try to do as less
 * 		static access as possible.
 */
@Deprecated(forRemoval = true)
public final class ApplicationDefinitionLoader {

    private static final Logger logger = LogManager.getLogger(ApplicationDefinitionLoader.class);

    private ApplicationDefinitionLoader() {}

    /**
     * Parses the configuration file specified by the {@link Settings#getConfigPath()} setting, configures all
     * appropriate system settings, and returns a generic {@link ApplicationDefinition}.
     *
     * @param localNodesToStart
     * 		the {@link Set} of local nodes to be started, if specified
     * @return an {@link ApplicationDefinition} specifying the application to be loaded and all related configuration
     * @throws ConfigurationException
     * 		if the configuration file specified by {@link Settings#getConfigPath()} does not exist
     */
    public static ApplicationDefinition load(
            final LegacyConfigProperties configurationProperties, final Set<Integer> localNodesToStart)
            throws ConfigurationException {
        CommonUtils.throwArgNull(configurationProperties, "configurationProperties");
        CommonUtils.throwArgNull(localNodesToStart, "localNodesToStart");

        final String swirldName = configurationProperties.swirldName().orElse("");

        final List<Address> bookData = Collections.synchronizedList(new ArrayList<>());
        configurationProperties
                .getAddressConfigs()
                .forEach(addressConfig -> handleAddressConfig(localNodesToStart, bookData, addressConfig));

        final AppStartParams appStartParams = configurationProperties
                .appConfig()
                .map(ApplicationDefinitionLoader::convertToStartParams)
                .orElseThrow(() -> new ConfigurationException("application config is missing"));

        return new ApplicationDefinition(
                swirldName,
                appStartParams.appParameters(),
                appStartParams.appJarFilename(),
                appStartParams.mainClassname(),
                appStartParams.appJarPath(),
                bookData);
    }

    private static AppStartParams convertToStartParams(final JarAppConfig appConfig) {
        final String[] appParameters = appConfig.params();
        // the line is: app, jarFilename, optionalParameters
        final String appJarFilename = appConfig.jarName();
        // this is a real .jar file, so load from data/apps/
        Path appJarPath = Settings.getInstance().getAppsDirPath().resolve(appJarFilename);
        String mainClassname = "";
        try (final JarFile jarFile = new JarFile(appJarPath.toFile())) {
            final Manifest manifest = jarFile.getManifest();
            final Attributes attributes = manifest.getMainAttributes();
            mainClassname = attributes.getValue("Main-Class");
        } catch (final Exception e) {
            CommonUtils.tellUserConsolePopup("ERROR", "ERROR: Couldn't load app " + appJarPath);
            logger.error(EXCEPTION.getMarker(), "Couldn't find Main-Class name in jar file {}", appJarPath, e);
        }
        return new AppStartParams(appParameters, appJarFilename, mainClassname, appJarPath);
    }

    private static void handleAddressConfig(
            final Set<Integer> localNodesToStart, final List<Address> bookData, final AddressConfig addressConfig) {
        // The set localNodesToStart contains the nodes set by the command line to start, if
        // none are passed, then IP addresses will be compared to determine which node to
        // start. If some are passed, then the IP addresses will be ignored. This must be
        // considered for ownHost
        final boolean isOwnHost;
        try {
            isOwnHost = (localNodesToStart.isEmpty() && Network.isOwn(addressConfig.internalInetAddressName()))
                    || localNodesToStart.contains(bookData.size());
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
        bookData.add(new Address(
                bookData.size(), // Id
                addressConfig.nickname(), // nickname
                addressConfig.selfName(), // selfName
                addressConfig.stake(), // stake
                isOwnHost, // ownHost
                addressConfig.internalInetAddressName().getAddress(), // addressInternalIpv4
                addressConfig.internalPort(), // portInternalIpv4
                addressConfig.externalInetAddressName().getAddress(), // addressExternalIpv4
                addressConfig.externalPort(), // portExternalIpv4
                addressConfig.memo() // memo, optional
                ));
        /**
         * the Id parameter above is the member ID, and in the current software, it is equal
         * to the position of the address in the list of addresses in the address book, and
         * is also equal to the comm ID. The comm ID is currently set to the position of the
         * address in the config.txt file: the first one has comm ID 0, the next has comm ID
         * 1, and so on. In future versions of the software, each member ID can be any long,
         * and they may not be contiguous numbers. But the comm IDs must always be the
         * numbers from 0 to N-1 for N members. The comm IDs can then be used with the
         * RandomGraph to select which members to connect to.
         */
    }
}
