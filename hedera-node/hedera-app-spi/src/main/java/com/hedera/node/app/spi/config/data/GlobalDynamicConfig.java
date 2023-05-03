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

package com.hedera.node.app.spi.config.data;

import com.hedera.hapi.node.base.AccountID;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * This class contains the properties that are part of the {@code GlobalDynamicProperties} class in the mono-service
 * module.
 */
@Deprecated
@ConfigData
public record GlobalDynamicConfig(
        @ConfigProperty int maxNftMetadataBytes,
        @ConfigProperty int maxBatchSizeBurn,
        @ConfigProperty int maxBatchSizeMint,
        @ConfigProperty int maxNftTransfersLen,
        @ConfigProperty int maxBatchSizeWipe,
        @ConfigProperty long maxNftQueryRange,
        @ConfigProperty boolean allowTreasuryToOwnNfts,
        @ConfigProperty int maxTokensPerAccount,
        @ConfigProperty int maxTokenRelsPerInfoQuery,
        @ConfigProperty int maxCustomFeesAllowed,
        @ConfigProperty int maxTokenSymbolUtf8Bytes,
        @ConfigProperty int maxTokenNameUtf8Bytes,
        @ConfigProperty int maxFileSizeKb,
        @ConfigProperty int cacheRecordsTtl,
        @ConfigProperty int balancesExportPeriodSecs,
        @ConfigProperty int ratesIntradayChangeLimitPercent,
        @ConfigProperty long nodeBalanceWarningThreshold,
        @ConfigProperty String pathToBalancesExportDir,
        @ConfigProperty boolean shouldExportBalances,
        @ConfigProperty boolean shouldExportTokenBalances,
        @ConfigProperty AccountID fundingAccount,
        // Address fundingAccountAddress, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty int maxTransfersLen,
        @ConfigProperty int maxTokenTransfersLen,
        @ConfigProperty int maxMemoUtf8Bytes,
        @ConfigProperty long maxTxnDuration,
        @ConfigProperty long minTxnDuration,
        @ConfigProperty int minValidityBuffer,
        @ConfigProperty long maxGasPerSec,
        // byte[] chainIdBytes, <- we currently do not support the data type (will be added by another PR)
        // Bytes32 chainIdBytes32, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty long defaultContractLifetime,
        @ConfigProperty String evmVersion,
        @ConfigProperty boolean dynamicEvmVersion,
        @ConfigProperty int feesTokenTransferUsageMultiplier,
        // boolean atLeastOneAutoRenewTargetType, <- we currently do not support the data type (will be added by another
        // PR)
        // boolean expireAccounts, <- we currently do not support the data type (will be added by another PR)
        // boolean expireContracts, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty int autoRenewNumberOfEntitiesToScan,
        @ConfigProperty int autoRenewMaxNumberOfEntitiesToRenewOrDelete,
        @ConfigProperty long autoRenewGracePeriod,
        @ConfigProperty long maxAutoRenewDuration,
        @ConfigProperty long minAutoRenewDuration,
        // Duration grpcMinAutoRenewDuration, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty int localCallEstRetBytes,
        @ConfigProperty boolean schedulingLongTermEnabled,
        @ConfigProperty long schedulingMaxTxnPerSecond,
        @ConfigProperty long schedulingMaxExpirationFutureSeconds,
        @ConfigProperty int scheduledTxExpiryTimeSecs,
        @ConfigProperty int messageMaxBytesAllowed,
        @ConfigProperty long maxPrecedingRecords,
        @ConfigProperty long maxFollowingRecords,
        // @ConfigProperty Set<HederaFunctionality> schedulingWhitelist,
        // @ConfigProperty Set<HederaFunctionality> systemContractsWithTopLevelSigsAccess,
        // CongestionMultipliers congestionMultipliers, <- we currently do not support the data type (will be added by
        // another PR)
        @ConfigProperty int feesMinCongestionPeriod,
        @ConfigProperty boolean areNftsEnabled,
        @ConfigProperty long maxNftMints,
        @ConfigProperty int maxXferBalanceChanges,
        @ConfigProperty int maxCustomFeeDepth,
        // ScaleFactor nftMintScaleFactor, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty String upgradeArtifactsLoc,
        @ConfigProperty boolean throttleByGas,
        @ConfigProperty int contractMaxRefundPercentOfGasLimit,
        @ConfigProperty long scheduleThrottleMaxGasLimit,
        @ConfigProperty long htsDefaultGasCost,
        @ConfigProperty int changeHistorianMemorySecs,
        @ConfigProperty boolean autoCreationEnabled,
        @ConfigProperty boolean expandSigsFromImmutableState,
        @ConfigProperty long maxAggregateContractKvPairs,
        @ConfigProperty int maxIndividualContractKvPairs,
        @ConfigProperty int maxMostRecentQueryableRecords,
        @ConfigProperty int maxAllowanceLimitPerTransaction,
        @ConfigProperty int maxAllowanceLimitPerAccount,
        @ConfigProperty boolean exportPrecompileResults,
        @ConfigProperty boolean create2Enabled,
        @ConfigProperty boolean redirectTokenCalls,
        @ConfigProperty boolean enableAllowances,
        @ConfigProperty boolean limitTokenAssociations,
        @ConfigProperty boolean enableHTSPrecompileCreate,
        @ConfigProperty boolean atomicCryptoTransferEnabled,
        // KnownBlockValues knownBlockValues, <- we currently do not support the data type (will be added by another PR)
        @ConfigProperty long exchangeRateGasReq,
        @ConfigProperty long stakingRewardRate,
        @ConfigProperty long stakingStartThreshold,
        @ConfigProperty int nodeRewardPercent,
        @ConfigProperty int stakingRewardPercent,
        @ConfigProperty boolean contractAutoAssociationsEnabled,
        @ConfigProperty boolean stakingEnabled,
        @ConfigProperty long maxDailyStakeRewardThPerH,
        @ConfigProperty int recordFileVersion,
        @ConfigProperty int recordSignatureFileVersion,
        @ConfigProperty long maxNumAccounts,
        @ConfigProperty long maxNumContracts,
        @ConfigProperty long maxNumFiles,
        @ConfigProperty long maxNumTokens,
        @ConfigProperty long maxNumTokenRels,
        @ConfigProperty long maxNumTopics,
        @ConfigProperty long maxNumSchedules,
        @ConfigProperty boolean utilPrngEnabled,
        // Set<SidecarType> enabledSidecars,
        @ConfigProperty boolean sidecarValidationEnabled,
        @ConfigProperty boolean requireMinStakeToReward,
        // Map<Long, Long> nodeMaxMinStakeRatios, <- we currently do not support the data type (will be added by another
        // PR)
        @ConfigProperty int sidecarMaxSizeMb,
        @ConfigProperty boolean itemizeStorageFees,
        // ContractStoragePriceTiers storagePriceTiers, <- we currently do not support the data type (will be added by
        // another PR)
        @ConfigProperty boolean compressRecordFilesOnCreation,
        @ConfigProperty boolean tokenAutoCreationsEnabled,
        @ConfigProperty boolean doTraceabilityExport,
        @ConfigProperty boolean compressAccountBalanceFilesOnCreation,
        @ConfigProperty long traceabilityMaxExportsPerConsSec,
        @ConfigProperty long traceabilityMinFreeToUsedGasThrottleRatio,
        @ConfigProperty boolean lazyCreationEnabled,
        @ConfigProperty boolean cryptoCreateWithAliasEnabled,
        @ConfigProperty boolean enforceContractCreationThrottle,
        // @ConfigProperty Set<Address> permittedDelegateCallers,
        // EntityScaleFactors entityScaleFactors, <- we currently do not support the data type (will be added by another
        // PR)
        @ConfigProperty long maxNumWithHapiSigsAccess
        // LegacyContractIdActivations legacyContractIdActivations, <- we currently do not support the data type (will
        // be added by another PR)
        // @ConfigProperty Set<Address> contractsWithSpecialHapiSigsAccess
) {
}
