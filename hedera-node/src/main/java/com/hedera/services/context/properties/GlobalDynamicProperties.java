/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.EntityType.ACCOUNT;
import static com.hedera.services.context.properties.EntityType.CONTRACT;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GlobalDynamicProperties {
    private final HederaNumbers hederaNums;
    private final PropertySource properties;

    private int maxNftMetadataBytes;
    private int maxBatchSizeBurn;
    private int maxBatchSizeMint;
    private int maxNftTransfersLen;
    private int maxBatchSizeWipe;
    private long maxNftQueryRange;
    private int maxTokensPerAccount;
    private int maxTokenRelsPerInfoQuery;
    private int maxCustomFeesAllowed;
    private int maxTokenSymbolUtf8Bytes;
    private int maxTokenNameUtf8Bytes;
    private int maxFileSizeKb;
    private int cacheRecordsTtl;
    private int balancesExportPeriodSecs;
    private int ratesIntradayChangeLimitPercent;
    private long maxAccountNum;
    private long nodeBalanceWarningThreshold;
    private String pathToBalancesExportDir;
    private boolean shouldExportBalances;
    private boolean shouldExportTokenBalances;
    private AccountID fundingAccount;
    private int maxTransfersLen;
    private int maxTokenTransfersLen;
    private int maxMemoUtf8Bytes;
    private long maxTxnDuration;
    private long minTxnDuration;
    private int minValidityBuffer;
    private long maxGasPerSec;
    private int chainId;
    private byte[] chainIdBytes;
    private long defaultContractLifetime;
    private int feesTokenTransferUsageMultiplier;
    private boolean atLeastOneAutoRenewTargetType;
    private boolean expireAccounts;
    private boolean expireContracts;
    private int autoRenewNumberOfEntitiesToScan;
    private int autoRenewMaxNumberOfEntitiesToRenewOrDelete;
    private long autoRenewGracePeriod;
    private long maxAutoRenewDuration;
    private long minAutoRenewDuration;
    private Duration grpcMinAutoRenewDuration;
    private int localCallEstRetBytes;
    private boolean schedulingLongTermEnabled;
    private long schedulingMaxTxnPerSecond;
    private long schedulingMaxExpirationFutureSeconds;
    private int scheduledTxExpiryTimeSecs;
    private int messageMaxBytesAllowed;
    private long maxPrecedingRecords;
    private long maxFollowingRecords;
    private Set<HederaFunctionality> schedulingWhitelist;
    private CongestionMultipliers congestionMultipliers;
    private int feesMinCongestionPeriod;
    private boolean areNftsEnabled;
    private long maxNftMints;
    private int maxXferBalanceChanges;
    private int maxCustomFeeDepth;
    private ThrottleReqOpsScaleFactor nftMintScaleFactor;
    private String upgradeArtifactsLoc;
    private boolean throttleByGas;
    private int contractMaxRefundPercentOfGasLimit;
    private long scheduleThrottleMaxGasLimit;
    private long htsDefaultGasCost;
    private int changeHistorianMemorySecs;
    private boolean autoCreationEnabled;
    private boolean expandSigsFromLastSignedState;
    private long maxAggregateContractKvPairs;
    private int maxIndividualContractKvPairs;
    private int maxMostRecentQueryableRecords;
    private int maxAllowanceLimitPerTransaction;
    private int maxAllowanceLimitPerAccount;
    private boolean exportPrecompileResults;
    private boolean create2Enabled;
    private boolean redirectTokenCalls;
    private boolean enableAllowances;
    private boolean limitTokenAssociations;
    private boolean enableHTSPrecompileCreate;
    private int maxPurgedKvPairsPerTouch;
    private KnownBlockValues knownBlockValues;
    private int maxReturnedNftsPerTouch;
    private long exchangeRateGasReq;
    private long stakingRewardRate;
    private long stakingStartThreshold;
    private int nodeRewardPercent;
    private int stakingRewardPercent;
    private boolean contractAutoAssociationsEnabled;
    private boolean stakingEnabled;
    private long maxDailyStakeRewardThPerH;
    private int recordFileVersion;
    private int recordSignatureFileVersion;
    private boolean prngEnabled;
    private Set<SidecarType> enabledSidecars;

    @Inject
    public GlobalDynamicProperties(
            HederaNumbers hederaNums, @CompositeProps PropertySource properties) {
        this.hederaNums = hederaNums;
        this.properties = properties;

        reload();
    }

    public void reload() {
        maxNftMetadataBytes = properties.getIntProperty("tokens.nfts.maxMetadataBytes");
        maxBatchSizeBurn = properties.getIntProperty("tokens.nfts.maxBatchSizeBurn");
        maxBatchSizeMint = properties.getIntProperty("tokens.nfts.maxBatchSizeMint");
        maxBatchSizeWipe = properties.getIntProperty("tokens.nfts.maxBatchSizeWipe");
        maxNftQueryRange = properties.getLongProperty("tokens.nfts.maxQueryRange");
        maxTokensPerAccount = properties.getIntProperty("tokens.maxPerAccount");
        maxTokenRelsPerInfoQuery = properties.getIntProperty("tokens.maxRelsPerInfoQuery");
        maxTokenSymbolUtf8Bytes = properties.getIntProperty("tokens.maxSymbolUtf8Bytes");
        maxTokenNameUtf8Bytes = properties.getIntProperty("tokens.maxTokenNameUtf8Bytes");
        maxAccountNum = properties.getLongProperty("ledger.maxAccountNum");
        maxFileSizeKb = properties.getIntProperty("files.maxSizeKb");
        fundingAccount =
                AccountID.newBuilder()
                        .setShardNum(hederaNums.shard())
                        .setRealmNum(hederaNums.realm())
                        .setAccountNum(properties.getLongProperty("ledger.fundingAccount"))
                        .build();
        cacheRecordsTtl = properties.getIntProperty("cache.records.ttl");
        ratesIntradayChangeLimitPercent =
                properties.getIntProperty("rates.intradayChangeLimitPercent");
        balancesExportPeriodSecs = properties.getIntProperty("balances.exportPeriodSecs");
        shouldExportBalances = properties.getBooleanProperty("balances.exportEnabled");
        nodeBalanceWarningThreshold =
                properties.getLongProperty("balances.nodeBalanceWarningThreshold");
        pathToBalancesExportDir = properties.getStringProperty("balances.exportDir.path");
        shouldExportTokenBalances = properties.getBooleanProperty("balances.exportTokenBalances");
        maxTransfersLen = properties.getIntProperty("ledger.transfers.maxLen");
        maxTokenTransfersLen = properties.getIntProperty("ledger.tokenTransfers.maxLen");
        maxNftTransfersLen = properties.getIntProperty("ledger.nftTransfers.maxLen");
        maxMemoUtf8Bytes = properties.getIntProperty("hedera.transaction.maxMemoUtf8Bytes");
        maxTxnDuration = properties.getLongProperty("hedera.transaction.maxValidDuration");
        minTxnDuration = properties.getLongProperty("hedera.transaction.minValidDuration");
        minValidityBuffer = properties.getIntProperty("hedera.transaction.minValidityBufferSecs");
        maxGasPerSec = properties.getLongProperty("contracts.maxGasPerSec");
        chainId = properties.getIntProperty("contracts.chainId");
        chainIdBytes = Integers.toBytes(chainId);
        defaultContractLifetime = properties.getLongProperty("contracts.defaultLifetime");
        feesTokenTransferUsageMultiplier =
                properties.getIntProperty("fees.tokenTransferUsageMultiplier");
        autoRenewNumberOfEntitiesToScan =
                properties.getIntProperty("autorenew.numberOfEntitiesToScan");
        autoRenewMaxNumberOfEntitiesToRenewOrDelete =
                properties.getIntProperty("autorenew.maxNumberOfEntitiesToRenewOrDelete");
        autoRenewGracePeriod = properties.getLongProperty("autorenew.gracePeriod");
        maxAutoRenewDuration = properties.getLongProperty("ledger.autoRenewPeriod.maxDuration");
        minAutoRenewDuration = properties.getLongProperty("ledger.autoRenewPeriod.minDuration");
        grpcMinAutoRenewDuration = Duration.newBuilder().setSeconds(minAutoRenewDuration).build();
        localCallEstRetBytes = properties.getIntProperty("contracts.localCall.estRetBytes");
        scheduledTxExpiryTimeSecs = properties.getIntProperty("ledger.schedule.txExpiryTimeSecs");
        schedulingLongTermEnabled = properties.getBooleanProperty("scheduling.longTermEnabled");
        schedulingMaxTxnPerSecond = properties.getLongProperty("scheduling.maxTxnPerSecond");
        schedulingMaxExpirationFutureSeconds =
                properties.getLongProperty("scheduling.maxExpirationFutureSeconds");
        schedulingWhitelist = properties.getFunctionsProperty("scheduling.whitelist");
        messageMaxBytesAllowed = properties.getIntProperty("consensus.message.maxBytesAllowed");
        maxPrecedingRecords = properties.getLongProperty("consensus.handle.maxPrecedingRecords");
        maxFollowingRecords = properties.getLongProperty("consensus.handle.maxFollowingRecords");
        congestionMultipliers =
                properties.getCongestionMultiplierProperty("fees.percentCongestionMultipliers");
        feesMinCongestionPeriod = properties.getIntProperty("fees.minCongestionPeriod");
        maxCustomFeesAllowed = properties.getIntProperty("tokens.maxCustomFeesAllowed");
        areNftsEnabled = properties.getBooleanProperty("tokens.nfts.areEnabled");
        maxNftMints = properties.getLongProperty("tokens.nfts.maxAllowedMints");
        maxXferBalanceChanges = properties.getIntProperty("ledger.xferBalanceChanges.maxLen");
        maxCustomFeeDepth = properties.getIntProperty("tokens.maxCustomFeeDepth");
        nftMintScaleFactor =
                properties.getThrottleScaleFactor("tokens.nfts.mintThrottleScaleFactor");
        upgradeArtifactsLoc = properties.getStringProperty("upgrade.artifacts.path");
        throttleByGas = properties.getBooleanProperty("contracts.throttle.throttleByGas");
        contractMaxRefundPercentOfGasLimit =
                properties.getIntProperty("contracts.maxRefundPercentOfGasLimit");
        scheduleThrottleMaxGasLimit =
                properties.getLongProperty("contracts.scheduleThrottleMaxGasLimit");
        htsDefaultGasCost = properties.getLongProperty("contracts.precompile.htsDefaultGasCost");
        changeHistorianMemorySecs = properties.getIntProperty("ledger.changeHistorian.memorySecs");
        autoCreationEnabled = properties.getBooleanProperty("autoCreation.enabled");
        expandSigsFromLastSignedState =
                properties.getBooleanProperty("sigs.expandFromLastSignedState");
        maxAggregateContractKvPairs = properties.getLongProperty("contracts.maxKvPairs.aggregate");
        maxIndividualContractKvPairs = properties.getIntProperty("contracts.maxKvPairs.individual");
        maxMostRecentQueryableRecords =
                properties.getIntProperty("ledger.records.maxQueryableByAccount");
        maxAllowanceLimitPerTransaction =
                properties.getIntProperty("hedera.allowances.maxTransactionLimit");
        maxAllowanceLimitPerAccount =
                properties.getIntProperty("hedera.allowances.maxAccountLimit");
        exportPrecompileResults =
                properties.getBooleanProperty("contracts.precompile.exportRecordResults");
        create2Enabled = properties.getBooleanProperty("contracts.allowCreate2");
        redirectTokenCalls = properties.getBooleanProperty("contracts.redirectTokenCalls");
        enabledSidecars = properties.getSidecarsProperty("contracts.sidecars");
        enableAllowances = properties.getBooleanProperty("hedera.allowances.isEnabled");
        final var autoRenewTargetTypes = properties.getTypesProperty("autoRenew.targetTypes");
        expireAccounts = autoRenewTargetTypes.contains(ACCOUNT);
        expireContracts = autoRenewTargetTypes.contains(CONTRACT);
        atLeastOneAutoRenewTargetType = !autoRenewTargetTypes.isEmpty();
        limitTokenAssociations = properties.getBooleanProperty("entities.limitTokenAssociations");
        enableHTSPrecompileCreate =
                properties.getBooleanProperty("contracts.precompile.htsEnableTokenCreate");
        maxPurgedKvPairsPerTouch = properties.getIntProperty("autoRemove.maxPurgedKvPairsPerTouch");
        maxReturnedNftsPerTouch = properties.getIntProperty("autoRemove.maxReturnedNftsPerTouch");
        knownBlockValues = properties.getBlockValuesProperty("contracts.knownBlockHash");
        exchangeRateGasReq = properties.getLongProperty("contracts.precompile.exchangeRateGasCost");
        stakingRewardRate = properties.getLongProperty("staking.rewardRate");
        stakingStartThreshold = properties.getLongProperty("staking.startThreshold");
        nodeRewardPercent = properties.getIntProperty("staking.fees.nodeRewardPercentage");
        stakingRewardPercent = properties.getIntProperty("staking.fees.stakingRewardPercentage");
        contractAutoAssociationsEnabled =
                properties.getBooleanProperty("contracts.allowAutoAssociations");
        maxDailyStakeRewardThPerH = properties.getLongProperty("staking.maxDailyStakeRewardThPerH");
        stakingEnabled = properties.getBooleanProperty("staking.isEnabled");
        recordFileVersion = properties.getIntProperty("hedera.recordStream.recordFileVersion");
        recordSignatureFileVersion =
                properties.getIntProperty("hedera.recordStream.signatureFileVersion");
        prngEnabled = properties.getBooleanProperty("prng.isEnabled");
    }

    public int maxTokensPerAccount() {
        return maxTokensPerAccount;
    }

    public int maxTokensRelsPerInfoQuery() {
        return maxTokenRelsPerInfoQuery;
    }

    public int maxCustomFeesAllowed() {
        return maxCustomFeesAllowed;
    }

    public int maxNftMetadataBytes() {
        return maxNftMetadataBytes;
    }

    public int maxBatchSizeBurn() {
        return maxBatchSizeBurn;
    }

    public int maxNftTransfersLen() {
        return maxNftTransfersLen;
    }

    public int maxBatchSizeWipe() {
        return maxBatchSizeWipe;
    }

    public int maxBatchSizeMint() {
        return maxBatchSizeMint;
    }

    public long maxNftQueryRange() {
        return maxNftQueryRange;
    }

    public int maxTokenSymbolUtf8Bytes() {
        return maxTokenSymbolUtf8Bytes;
    }

    public long maxAccountNum() {
        return maxAccountNum;
    }

    public int maxTokenNameUtf8Bytes() {
        return maxTokenNameUtf8Bytes;
    }

    public int maxFileSizeKb() {
        return maxFileSizeKb;
    }

    public AccountID fundingAccount() {
        return fundingAccount;
    }

    public int cacheRecordsTtl() {
        return cacheRecordsTtl;
    }

    public int ratesIntradayChangeLimitPercent() {
        return ratesIntradayChangeLimitPercent;
    }

    public int balancesExportPeriodSecs() {
        return balancesExportPeriodSecs;
    }

    public boolean shouldExportBalances() {
        return shouldExportBalances;
    }

    public long nodeBalanceWarningThreshold() {
        return nodeBalanceWarningThreshold;
    }

    public String pathToBalancesExportDir() {
        return pathToBalancesExportDir;
    }

    public boolean shouldExportTokenBalances() {
        return shouldExportTokenBalances;
    }

    public int maxTransferListSize() {
        return maxTransfersLen;
    }

    public int maxTokenTransferListSize() {
        return maxTokenTransfersLen;
    }

    public int maxMemoUtf8Bytes() {
        return maxMemoUtf8Bytes;
    }

    public long maxTxnDuration() {
        return maxTxnDuration;
    }

    public long minTxnDuration() {
        return minTxnDuration;
    }

    public int minValidityBuffer() {
        return minValidityBuffer;
    }

    public long maxGasPerSec() {
        return maxGasPerSec;
    }

    public int chainId() {
        return chainId;
    }

    public byte[] chainIdBytes() {
        return chainIdBytes;
    }

    public long defaultContractLifetime() {
        return defaultContractLifetime;
    }

    public int feesTokenTransferUsageMultiplier() {
        return feesTokenTransferUsageMultiplier;
    }

    public boolean shouldAutoRenewSomeEntityType() {
        return atLeastOneAutoRenewTargetType;
    }

    public int autoRenewNumberOfEntitiesToScan() {
        return autoRenewNumberOfEntitiesToScan;
    }

    public int autoRenewMaxNumberOfEntitiesToRenewOrDelete() {
        return autoRenewMaxNumberOfEntitiesToRenewOrDelete;
    }

    public long autoRenewGracePeriod() {
        return autoRenewGracePeriod;
    }

    public long maxAutoRenewDuration() {
        return maxAutoRenewDuration;
    }

    public long minAutoRenewDuration() {
        return minAutoRenewDuration;
    }

    public Duration typedMinAutoRenewDuration() {
        return grpcMinAutoRenewDuration;
    }

    public int localCallEstRetBytes() {
        return localCallEstRetBytes;
    }

    public int scheduledTxExpiryTimeSecs() {
        return scheduledTxExpiryTimeSecs;
    }

    public boolean schedulingLongTermEnabled() {
        return schedulingLongTermEnabled;
    }

    public long schedulingMaxTxnPerSecond() {
        return schedulingMaxTxnPerSecond;
    }

    public long schedulingMaxExpirationFutureSeconds() {
        return schedulingMaxExpirationFutureSeconds;
    }

    public int messageMaxBytesAllowed() {
        return messageMaxBytesAllowed;
    }

    public long maxPrecedingRecords() {
        return maxPrecedingRecords;
    }

    public long maxFollowingRecords() {
        return maxFollowingRecords;
    }

    public Set<HederaFunctionality> schedulingWhitelist() {
        return schedulingWhitelist;
    }

    public CongestionMultipliers congestionMultipliers() {
        return congestionMultipliers;
    }

    public int feesMinCongestionPeriod() {
        return feesMinCongestionPeriod;
    }

    public boolean areNftsEnabled() {
        return areNftsEnabled;
    }

    public long maxNftMints() {
        return maxNftMints;
    }

    public int maxXferBalanceChanges() {
        return maxXferBalanceChanges;
    }

    public int maxCustomFeeDepth() {
        return maxCustomFeeDepth;
    }

    public ThrottleReqOpsScaleFactor nftMintScaleFactor() {
        return nftMintScaleFactor;
    }

    public String upgradeArtifactsLoc() {
        return upgradeArtifactsLoc;
    }

    public boolean shouldThrottleByGas() {
        return throttleByGas;
    }

    public int maxGasRefundPercentage() {
        return contractMaxRefundPercentOfGasLimit;
    }

    public long scheduleThrottleMaxGasLimit() {
        return scheduleThrottleMaxGasLimit;
    }

    public long htsDefaultGasCost() {
        return htsDefaultGasCost;
    }

    public int changeHistorianMemorySecs() {
        return changeHistorianMemorySecs;
    }

    public boolean isAutoCreationEnabled() {
        return autoCreationEnabled;
    }

    public boolean expandSigsFromLastSignedState() {
        return expandSigsFromLastSignedState;
    }

    public long maxAggregateContractKvPairs() {
        return maxAggregateContractKvPairs;
    }

    public int maxIndividualContractKvPairs() {
        return maxIndividualContractKvPairs;
    }

    public int maxNumQueryableRecords() {
        return maxMostRecentQueryableRecords;
    }

    public int maxAllowanceLimitPerTransaction() {
        return maxAllowanceLimitPerTransaction;
    }

    public int maxAllowanceLimitPerAccount() {
        return maxAllowanceLimitPerAccount;
    }

    public boolean shouldExportPrecompileResults() {
        return exportPrecompileResults;
    }

    public boolean isCreate2Enabled() {
        return create2Enabled;
    }

    public boolean isRedirectTokenCallsEnabled() {
        return redirectTokenCalls;
    }

    public boolean areAllowancesEnabled() {
        return enableAllowances;
    }

    public boolean shouldAutoRenewContracts() {
        return expireContracts;
    }

    public boolean shouldAutoRenewAccounts() {
        return expireAccounts;
    }

    public boolean areTokenAssociationsLimited() {
        return limitTokenAssociations;
    }

    public boolean isHTSPrecompileCreateEnabled() {
        return enableHTSPrecompileCreate;
    }

    public int getMaxPurgedKvPairsPerTouch() {
        return maxPurgedKvPairsPerTouch;
    }

    public KnownBlockValues knownBlockValues() {
        return knownBlockValues;
    }

    public int getMaxReturnedNftsPerTouch() {
        return maxReturnedNftsPerTouch;
    }

    public long exchangeRateGasReq() {
        return exchangeRateGasReq;
    }

    public long getStakingRewardRate() {
        return stakingRewardRate;
    }

    public long getStakingStartThreshold() {
        return stakingStartThreshold;
    }

    public int getNodeRewardPercent() {
        return nodeRewardPercent;
    }

    public int getStakingRewardPercent() {
        return stakingRewardPercent;
    }

    public boolean areContractAutoAssociationsEnabled() {
        return contractAutoAssociationsEnabled;
    }

    public boolean isStakingEnabled() {
        return stakingEnabled;
    }

    public long maxDailyStakeRewardThPerH() {
        return maxDailyStakeRewardThPerH;
    }

    public int recordFileVersion() {
        return recordFileVersion;
    }

    public int recordSignatureFileVersion() {
        return recordSignatureFileVersion;
    }

    public boolean isPrngEnabled() {
        return prngEnabled;
    }

    public Set<SidecarType> enabledSidecars() {
        return enabledSidecars;
    }
}
