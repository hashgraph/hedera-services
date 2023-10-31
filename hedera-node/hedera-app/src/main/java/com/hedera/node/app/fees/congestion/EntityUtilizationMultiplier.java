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

package com.hedera.node.app.fees.congestion;

import static com.hedera.node.app.service.mono.context.properties.EntityType.ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.EntityType.CONTRACT;
import static com.hedera.node.app.service.mono.context.properties.EntityType.FILE;
import static com.hedera.node.app.service.mono.context.properties.EntityType.NFT;
import static com.hedera.node.app.service.mono.context.properties.EntityType.TOKEN;
import static com.hedera.node.app.service.mono.context.properties.EntityType.TOKEN_ASSOCIATION;
import static com.hedera.node.app.service.mono.context.properties.EntityType.TOPIC;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.data.TopicsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An implementation wrapping a {@link ThrottleMultiplier} and applying an additional scaling factor based on
 * the number of entities saved in state.
 */
public class EntityUtilizationMultiplier {
    private final ThrottleMultiplier delegate;
    private final ConfigProvider configProvider;

    public EntityUtilizationMultiplier(
            @NonNull final ThrottleMultiplier delegate, @NonNull final ConfigProvider configProvider) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    /**
     * Returns the current congestion multiplier applying additional scaling factor based on
     * the number of entities saved in state.
     *
     *  @param txnInfo transaction info
     *  @param state the state needed for determining entity utilization
     *
     * @return the current congestion multiplier
     */
    public long currentMultiplier(@NonNull final TransactionInfo txnInfo, @NonNull final HederaState state) {
        final var throttleMultiplier = delegate.currentMultiplier();
        final var configuration = configProvider.getConfiguration();
        final var entityScaleFactors =
                configuration.getConfigData(FeesConfig.class).percentUtilizationScaleFactors();

        return switch (txnInfo.functionality()) {
            case CRYPTO_CREATE -> entityScaleFactors
                    .scaleForNew(ACCOUNT, roundedAccountPercentUtil(state))
                    .scaling((int) throttleMultiplier);
            case CONTRACT_CREATE -> entityScaleFactors
                    .scaleForNew(CONTRACT, roundedContractPercentUtil(state))
                    .scaling((int) throttleMultiplier);
            case FILE_CREATE -> entityScaleFactors
                    .scaleForNew(FILE, roundedFilePercentUtil(state))
                    .scaling((int) throttleMultiplier);
            case TOKEN_MINT -> {
                final var mintsWithMetadata =
                        !txnInfo.txBody().tokenMint().metadata().isEmpty();
                yield mintsWithMetadata
                        ? entityScaleFactors
                                .scaleForNew(NFT, roundedNftPercentUtil(state))
                                .scaling((int) throttleMultiplier)
                        : throttleMultiplier;
            }
            case TOKEN_CREATE -> entityScaleFactors
                    .scaleForNew(TOKEN, roundedTokenPercentUtil(state))
                    .scaling((int) throttleMultiplier);
            case TOKEN_ASSOCIATE_TO_ACCOUNT -> entityScaleFactors
                    .scaleForNew(TOKEN_ASSOCIATION, roundedTokenRelPercentUtil(state))
                    .scaling((int) throttleMultiplier);
            case CONSENSUS_CREATE_TOPIC -> entityScaleFactors
                    .scaleForNew(TOPIC, roundedTopicPercentUtil(state))
                    .scaling((int) throttleMultiplier);
            default -> throttleMultiplier;
        };
    }

    private int roundedAccountPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfAccounts =
                configuration.getConfigData(AccountsConfig.class).maxNumber();

        final var writableAccountStoreFactory = new WritableStoreFactory(state, TokenService.NAME);
        final var accountsStore = writableAccountStoreFactory.getStore(WritableAccountStore.class);
        final var numAccountsAndContracts = accountsStore.sizeOfAccountState();

        final var writableContractStoreFactory = new WritableStoreFactory(state, ContractService.NAME);
        final var contractsStore = writableContractStoreFactory.getStore(WritableContractStateStore.class);
        final var numContracts = contractsStore.getNumBytecodes();
        final var numAccounts = numAccountsAndContracts - numContracts;

        return maxNumOfAccounts == 0 ? 100 : (int) ((100 * numAccounts) / maxNumOfAccounts);
    }

    private int roundedContractPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfContracts =
                configuration.getConfigData(ContractsConfig.class).maxNumber();

        final var writableContractStoreFactory = new WritableStoreFactory(state, ContractService.NAME);
        final var contractsStore = writableContractStoreFactory.getStore(WritableContractStateStore.class);
        final var numContracts = contractsStore.getNumBytecodes();

        return maxNumOfContracts == 0 ? 100 : (int) ((100 * numContracts) / maxNumOfContracts);
    }

    private int roundedFilePercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfFiles = configuration.getConfigData(FilesConfig.class).maxNumber();

        final var writableFileStoreFactory = new WritableStoreFactory(state, FileService.NAME);
        final var fileStore = writableFileStoreFactory.getStore(WritableFileStore.class);
        final var numOfFiles = fileStore.sizeOfState();

        return maxNumOfFiles == 0 ? 100 : (int) ((100 * numOfFiles) / maxNumOfFiles);
    }

    private int roundedNftPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfNfts = configuration.getConfigData(TokensConfig.class).nftsMaxAllowedMints();

        final var writableNftStoreFactory = new WritableStoreFactory(state, TokenService.NAME);
        final var nftStore = writableNftStoreFactory.getStore(WritableNftStore.class);
        final var numOfNfts = nftStore.sizeOfState();

        return maxNumOfNfts == 0 ? 100 : (int) ((100 * numOfNfts) / maxNumOfNfts);
    }

    private int roundedTokenPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfTokens =
                configuration.getConfigData(TokensConfig.class).maxNumber();

        final var writableTokenStoreFactory = new WritableStoreFactory(state, TokenService.NAME);
        final var tokenStore = writableTokenStoreFactory.getStore(WritableTokenStore.class);
        final var numOfTokens = tokenStore.sizeOfState();

        return maxNumOfTokens == 0 ? 100 : (int) ((100 * numOfTokens) / maxNumOfTokens);
    }

    private int roundedTokenRelPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfTokenRels =
                configuration.getConfigData(TokensConfig.class).maxAggregateRels();

        final var writableTokenRelsStoreFactory = new WritableStoreFactory(state, TokenService.NAME);
        final var tokenRelStore = writableTokenRelsStoreFactory.getStore(WritableTokenRelationStore.class);
        final var numOfTokensRels = tokenRelStore.sizeOfState();

        return maxNumOfTokenRels == 0 ? 100 : (int) ((100 * numOfTokensRels) / maxNumOfTokenRels);
    }

    private int roundedTopicPercentUtil(@NonNull final HederaState state) {
        final var configuration = configProvider.getConfiguration();
        final var maxNumberOfTopics =
                configuration.getConfigData(TopicsConfig.class).maxNumber();

        final var writableTopicsStoreFactory = new WritableStoreFactory(state, ConsensusService.NAME);
        final var topicStore = writableTopicsStoreFactory.getStore(WritableTopicStore.class);
        final var numOfTopics = topicStore.sizeOfState();

        return maxNumberOfTopics == 0 ? 100 : (int) ((100 * numOfTopics) / maxNumberOfTopics);
    }

    /**
     * Updates the congestion multiplier for the given consensus time.
     *
     * @param consensusTime the consensus time
     */
    public void updateMultiplier(@NonNull final Instant consensusTime) {
        delegate.updateMultiplier(consensusTime);
    }

    /**
     * Rebuilds the internal state of the ThrottleMultiplier.
     */
    public void resetExpectations() {
        delegate.resetExpectations();
    }

    /**
     * Resets the congestion level starts to the given values.
     *
     * @param startTimes the saved congestion level starts
     */
    public void resetCongestionLevelStarts(@NonNull final Instant[] startTimes) {
        delegate.resetCongestionLevelStarts(startTimes);
    }

    /**
     * Returns the congestion level starts.
     *
     * @return the congestion level starts
     */
    @NonNull
    public Instant[] congestionLevelStarts() {
        return delegate.congestionLevelStarts();
    }
}
