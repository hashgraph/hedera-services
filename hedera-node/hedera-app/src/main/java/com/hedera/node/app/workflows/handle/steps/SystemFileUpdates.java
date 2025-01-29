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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.util.FileUtilities.observePropertiesAndPermissions;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple facility that notifies interested parties when a special file is updated.
 *
 * <p>This is a temporary solution. In the future we want to have specific transactions
 * to update the data that is currently transmitted in these files.
 */
@Singleton
public class SystemFileUpdates {
    private static final Logger logger = LogManager.getLogger(SystemFileUpdates.class);

    private final ConfigProviderImpl configProvider;
    private final ExchangeRateManager exchangeRateManager;
    private final FeeManager feeManager;
    private final ThrottleServiceManager throttleServiceManager;

    /**
     * Creates a new instance of this class.
     *
     * @param configProvider the configuration provider
     */
    @Inject
    public SystemFileUpdates(
            @NonNull final ConfigProviderImpl configProvider,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final FeeManager feeManager,
            @NonNull final ThrottleServiceManager throttleServiceManager) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.throttleServiceManager = requireNonNull(throttleServiceManager);
    }

    /**
     * Checks whether the given transaction body is a file update or file append of a special file and eventually
     * notifies the registered facility.
     *
     * @param state the current state (the updated file content needs to be committed to the state)
     * @param txBody the transaction body
     */
    public ResponseCodeEnum handleTxBody(@NonNull final State state, @NonNull final TransactionBody txBody) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");

        // Try to extract the file ID from the transaction body, if it is FileUpdate or FileAppend.
        final FileID fileID;
        if (txBody.hasFileUpdate()) {
            fileID = txBody.fileUpdateOrThrow().fileIDOrThrow();
        } else if (txBody.hasFileAppend()) {
            fileID = txBody.fileAppendOrThrow().fileIDOrThrow();
        } else {
            return SUCCESS;
        }

        // Check if the file is a special file
        final var configuration = configProvider.getConfiguration();
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var fileNum = fileID.fileNum();
        final var payer = txBody.transactionIDOrThrow().accountIDOrThrow();
        if (fileNum > ledgerConfig.numReservedSystemEntities()) {
            return SUCCESS;
        }

        // If it is a special file, call the updater.
        // We load the file only, if there is an updater for it.
        final var filesConfig = configuration.getConfigData(FilesConfig.class);

        if (fileNum == filesConfig.feeSchedules()) {
            return feeManager.update(FileUtilities.getFileContent(state, fileID));
        } else if (fileNum == filesConfig.exchangeRates()) {
            exchangeRateManager.update(FileUtilities.getFileContent(state, fileID), payer);
        } else if (fileNum == filesConfig.networkProperties()) {
            updateConfig(configuration, ConfigType.NETWORK_PROPERTIES, state);
            throttleServiceManager.refreshThrottleConfiguration();
        } else if (fileNum == filesConfig.hapiPermissions()) {
            updateConfig(configuration, ConfigType.API_PERMISSIONS, state);
        } else if (fileNum == filesConfig.throttleDefinitions()) {
            return throttleServiceManager.recreateThrottles(FileUtilities.getFileContent(state, fileID));
        }
        return SUCCESS;
    }

    private enum ConfigType {
        NETWORK_PROPERTIES,
        API_PERMISSIONS,
    }

    private void updateConfig(
            @NonNull final Configuration configuration,
            @NonNull final ConfigType configType,
            @NonNull final State state) {
        observePropertiesAndPermissions(state, configuration, (properties, permissions) -> {
            configProvider.update(properties, permissions);
            if (configType == ConfigType.NETWORK_PROPERTIES) {
                logContentsOf("Network properties", properties);
            } else {
                logContentsOf("API permissions", permissions);
            }
        });
    }

    private void logContentsOf(@NonNull final String configFileName, @NonNull final Bytes contents) {
        try {
            final var configList = ServicesConfigurationList.PROTOBUF.parseStrict(contents.toReadableSequentialData());
            final var printableConfigList = configList.nameValue().stream()
                    .map(pair -> pair.name() + "=" + pair.value())
                    .collect(joining("\n\t"));
            logger.info(
                    "Refreshing properties with following overrides to {}:\n\t{}",
                    configFileName,
                    printableConfigList.isBlank() ? "<NONE>" : printableConfigList);
        } catch (ParseException ignore) {
            // If this isn't parseable we won't have updated anything, also don't log
        }
    }
}
