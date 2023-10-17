/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.congestion.MonoMultiplierSources;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleManager;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple facility that notifies interested parties when a special file is updated.
 *
 * <p>This is a temporary solution. In the future we want to have specific transactions
 * to update the data that is currently transmitted in these files.
 */
public class SystemFileUpdateFacility {

    private static final Logger logger = LogManager.getLogger(SystemFileUpdateFacility.class);

    private final ConfigProviderImpl configProvider;
    private final ThrottleManager throttleManager;
    private final ExchangeRateManager exchangeRateManager;
    private final MonoMultiplierSources monoMultiplierSources;
    private final ThrottleAccumulator backendThrottle;
    private final ThrottleAccumulator frontendThrottle;

    /**
     * Creates a new instance of this class.
     *
     * @param configProvider the configuration provider
     */
    public SystemFileUpdateFacility(
            @NonNull final ConfigProviderImpl configProvider,
            @NonNull final ThrottleManager throttleManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final MonoMultiplierSources monoMultiplierSources,
            @NonNull final ThrottleAccumulator backendThrottle,
            @NonNull final ThrottleAccumulator frontendThrottle) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.throttleManager = requireNonNull(throttleManager, " throttleManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.monoMultiplierSources = requireNonNull(monoMultiplierSources, "multiplierSources must not be null");
        this.backendThrottle = requireNonNull(backendThrottle, "backendThrottle must not be null");
        this.frontendThrottle = requireNonNull(frontendThrottle, "frontendThrottle must not be null");
    }

    /**
     * Checks whether the given transaction body is a file update or file append of a special file and eventually
     * notifies the registered facility.
     *
     * @param state the current state (the updated file content needs to be committed to the state)
     * @param txBody the transaction body
     */
    public void handleTxBody(@NonNull final HederaState state, @NonNull final TransactionBody txBody) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");

        // Try to extract the file ID from the transaction body, if it is FileUpdate or FileAppend.
        final FileID fileID;
        if (txBody.hasFileUpdate()) {
            fileID = txBody.fileUpdateOrThrow().fileIDOrThrow();
        } else if (txBody.hasFileAppend()) {
            fileID = txBody.fileAppendOrThrow().fileIDOrThrow();
        } else {
            return;
        }

        // Check if the file is a special file
        final var configuration = configProvider.getConfiguration();
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var fileNum = fileID.fileNum();
        final var payer = txBody.transactionIDOrThrow().accountIDOrThrow();
        if (fileNum > ledgerConfig.numReservedSystemEntities()) {
            return;
        }

        // If it is a special file, call the updater.
        // We load the file only, if there is an updater for it.
        final var config = configuration.getConfigData(FilesConfig.class);
        try {
            if (fileNum == config.addressBook()) {
                logger.error("Update of address book not implemented");
            } else if (fileNum == config.nodeDetails()) {
                logger.error("Update of node details not implemented");
            } else if (fileNum == config.feeSchedules()) {
                logger.error("Update of fee schedules not implemented");
            } else if (fileNum == config.exchangeRates()) {
                exchangeRateManager.update(FileUtilities.getFileContent(state, fileID), payer);
            } else if (fileNum == config.networkProperties()) {
                configProvider.update(FileUtilities.getFileContent(state, fileID));
                backendThrottle.applyGasConfig();
                frontendThrottle.applyGasConfig();

                // Updating the multiplier source to use the new gas throttle
                // values that are coming from the network properties
                monoMultiplierSources.resetExpectations();
            } else if (fileNum == config.hapiPermissions()) {
                logger.error("Update of HAPI permissions not implemented");
            } else if (fileNum == config.throttleDefinitions()) {
                throttleManager.update(FileUtilities.getFileContent(state, fileID));
                backendThrottle.rebuildFor(throttleManager.throttleDefinitions());
                frontendThrottle.rebuildFor(throttleManager.throttleDefinitions());

                // Updating the multiplier source to use the new throttle definitions
                monoMultiplierSources.resetExpectations();
            } else if (fileNum == config.upgradeFileNumber()) {
                logger.error("Update of file number not implemented");
            }
        } catch (HandleException e) {
            // handle exception suppose to propagate the exception to the caller
            throw e;
        } catch (final RuntimeException e) {
            logger.warn(
                    "Exception while calling updater for file {}. " + "If the file is incomplete, this is expected.",
                    fileID,
                    e);
        }
    }
}
