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
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.fees.congestion.ThrottleMultiplierSource;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.data.TopicsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public class TransactionRateMultiplierSource implements FeeMultiplierSource {
    private final HederaState state;
    private final ConfigProvider configProvider;
    private final ThrottleMultiplierSource delegate;

    public TransactionRateMultiplierSource(
            @NonNull final ThrottleMultiplierSource delegate,
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaState state) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.state = requireNonNull(state, "state must not be null");
    }

    @Override
    public long currentMultiplier(TxnAccessor accessor) {
        final var throttleMultiplier = delegate.currentMultiplier(accessor);
        final var configuration = configProvider.getConfiguration();
        final var entityScaleFactors =
                configuration.getConfigData(FeesConfig.class).percentUtilizationScaleFactors();

        return switch (accessor.getFunction()) {
            case CryptoCreate -> entityScaleFactors
                    .scaleForNew(ACCOUNT, roundedAccountPercentUtil())
                    .scaling((int) throttleMultiplier);
            case ContractCreate -> entityScaleFactors
                    .scaleForNew(CONTRACT, roundedContractPercentUtil())
                    .scaling((int) throttleMultiplier);
            case FileCreate -> entityScaleFactors
                    .scaleForNew(FILE, roundedFilePercentUtil())
                    .scaling((int) throttleMultiplier);
            case TokenMint -> accessor.mintsWithMetadata()
                    ? entityScaleFactors
                            .scaleForNew(NFT, roundedNftPercentUtil())
                            .scaling((int) throttleMultiplier)
                    : throttleMultiplier;
            case TokenCreate -> entityScaleFactors
                    .scaleForNew(TOKEN, roundedTokenPercentUtil())
                    .scaling((int) throttleMultiplier);
            case TokenAssociateToAccount -> entityScaleFactors
                    .scaleForNew(TOKEN_ASSOCIATION, roundedTokenRelPercentUtil())
                    .scaling((int) throttleMultiplier);
            case ConsensusCreateTopic -> entityScaleFactors
                    .scaleForNew(TOPIC, roundedTopicPercentUtil())
                    .scaling((int) throttleMultiplier);
            default -> throttleMultiplier;
        };
    }

    private int roundedAccountPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfAccounts =
                configuration.getConfigData(AccountsConfig.class).maxNumber();

        final var stack = new SavepointStackImpl(state);
        final var writableAccountStoreFactory = new WritableStoreFactory(stack, TokenService.NAME);
        final var accountsStore = writableAccountStoreFactory.getStore(WritableAccountStore.class);
        final var numAccountsAndContracts = accountsStore.sizeOfAccountState();

        final var writableContractStoreFactory = new WritableStoreFactory(stack, ContractService.NAME);
        final var contractsStore = writableContractStoreFactory.getStore(WritableContractStateStore.class);
        final var numContracts = contractsStore.getNumBytecodes();
        final var numAccounts = numAccountsAndContracts - numContracts;

        return maxNumOfAccounts == 0 ? 100 : (int) ((100 * numAccounts) / maxNumOfAccounts);
    }

    private int roundedContractPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfContracts =
                configuration.getConfigData(ContractsConfig.class).maxNumber();

        final var stack = new SavepointStackImpl(state);
        final var writableContractStoreFactory = new WritableStoreFactory(stack, ContractService.NAME);
        final var contractsStore = writableContractStoreFactory.getStore(WritableContractStateStore.class);
        final var numContracts = contractsStore.getNumBytecodes();

        return maxNumOfContracts == 0 ? 100 : (int) ((100 * numContracts) / maxNumOfContracts);
    }

    private int roundedFilePercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfFiles = configuration.getConfigData(FilesConfig.class).maxNumber();

        final var stack = new SavepointStackImpl(state);
        final var writableFileStoreFactory = new WritableStoreFactory(stack, FileService.NAME);
        final var fileStore = writableFileStoreFactory.getStore(WritableFileStore.class);
        final var numOfFiles = fileStore.sizeOfState();

        return maxNumOfFiles == 0 ? 100 : (int) ((100 * numOfFiles) / maxNumOfFiles);
    }

    private int roundedNftPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfNfts = configuration.getConfigData(TokensConfig.class).nftsMaxAllowedMints();

        final var stack = new SavepointStackImpl(state);
        final var writableNftStoreFactory = new WritableStoreFactory(stack, TokenService.NAME);
        final var nftStore = writableNftStoreFactory.getStore(WritableNftStore.class);
        final var numOfNfts = nftStore.sizeOfState();

        return maxNumOfNfts == 0 ? 100 : (int) ((100 * numOfNfts) / maxNumOfNfts);
    }

    private int roundedTokenPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfTokens =
                configuration.getConfigData(TokensConfig.class).maxNumber();

        final var stack = new SavepointStackImpl(state);
        final var writableTokenStoreFactory = new WritableStoreFactory(stack, TokenService.NAME);
        final var tokenStore = writableTokenStoreFactory.getStore(WritableTokenStore.class);
        final var numOfTokens = tokenStore.sizeOfState();

        return maxNumOfTokens == 0 ? 100 : (int) ((100 * numOfTokens) / maxNumOfTokens);
    }

    private int roundedTokenRelPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumOfTokenRels =
                configuration.getConfigData(TokensConfig.class).maxAggregateRels();

        final var stack = new SavepointStackImpl(state);
        final var writableTokenRelsStoreFactory = new WritableStoreFactory(stack, TokenService.NAME);
        final var tokenRelStore = writableTokenRelsStoreFactory.getStore(WritableTokenRelationStore.class);
        final var numOfTokensRels = tokenRelStore.sizeOfState();

        return maxNumOfTokenRels == 0 ? 100 : (int) ((100 * numOfTokensRels) / maxNumOfTokenRels);
    }

    private int roundedTopicPercentUtil() {
        final var configuration = configProvider.getConfiguration();
        final var maxNumberOfTopics =
                configuration.getConfigData(TopicsConfig.class).maxNumber();

        final var stack = new SavepointStackImpl(state);
        final var writableTopicsStoreFactory = new WritableStoreFactory(stack, ConsensusService.NAME);
        final var topicStore = writableTopicsStoreFactory.getStore(WritableTopicStore.class);
        final var numOfTopics = topicStore.sizeOfState();

        return maxNumberOfTopics == 0 ? 100 : (int) ((100 * numOfTopics) / maxNumberOfTopics);
    }

    @Override
    public void updateMultiplier(TxnAccessor accessor, Instant consensusNow) {
        delegate.updateMultiplier(accessor, consensusNow);
    }

    @Override
    public void resetExpectations() {
        delegate.resetExpectations();
    }

    @Override
    public void resetCongestionLevelStarts(Instant[] savedStartTimes) {
        delegate.resetCongestionLevelStarts(savedStartTimes);
    }

    @Override
    public Instant[] congestionLevelStarts() {
        return delegate.congestionLevelStarts();
    }
}
