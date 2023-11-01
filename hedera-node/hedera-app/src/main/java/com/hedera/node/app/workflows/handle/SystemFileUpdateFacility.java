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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleManager;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
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
    private final FeeManager feeManager;
    private final CongestionMultipliers congestionMultipliers;
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
            @NonNull final FeeManager feeManager,
            @NonNull final CongestionMultipliers congestionMultipliers,
            @NonNull final ThrottleAccumulator backendThrottle,
            @NonNull final ThrottleAccumulator frontendThrottle) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.throttleManager = requireNonNull(throttleManager, " throttleManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.congestionMultipliers = requireNonNull(congestionMultipliers, "congestionMultipliers must not be null");
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
    public ResponseCodeEnum handleTxBody(@NonNull final HederaState state, @NonNull final TransactionBody txBody) {
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
        final var config = configuration.getConfigData(FilesConfig.class);

        if (fileNum == config.feeSchedules()) {
            return feeManager.update(FileUtilities.getFileContent(state, fileID));
        } else if (fileNum == config.exchangeRates()) {
            exchangeRateManager.update(FileUtilities.getFileContent(state, fileID), payer);
        } else if (fileNum == config.networkProperties()) {
            final var networkProperties = FileUtilities.getFileContent(state, fileID);
            final var permissions =
                    FileUtilities.getFileContent(state, createFileID(config.hapiPermissions(), configuration));
            configProvider.update(networkProperties, permissions);
            backendThrottle.applyGasConfig();
            frontendThrottle.applyGasConfig();

            // Updating the multiplier source to use the new gas throttle
            // values that are coming from the network properties
            congestionMultipliers.resetExpectations();
        } else if (fileNum == config.hapiPermissions()) {
            final var networkProperties =
                    FileUtilities.getFileContent(state, createFileID(config.networkProperties(), configuration));
            final var permissions = FileUtilities.getFileContent(state, fileID);
            configProvider.update(networkProperties, permissions);
        } else if (fileNum == config.throttleDefinitions()) {
            final var result = throttleManager.update(FileUtilities.getFileContent(state, fileID));
            backendThrottle.rebuildFor(throttleManager.throttleDefinitions());
            frontendThrottle.rebuildFor(throttleManager.throttleDefinitions());

            // Updating the multiplier source to use the new throttle definitions
            congestionMultipliers.resetExpectations();
            return result;
        }
        return SUCCESS;
    }

    private FileID createFileID(final long fileNum, @NonNull final Configuration configuration) {
        final var hederaConfig = configuration.getConfigData(HederaConfig.class);
        return FileID.newBuilder()
                .realmNum(hederaConfig.realm())
                .shardNum(hederaConfig.shard())
                .fileNum(fileNum)
                .build();
    }
}
