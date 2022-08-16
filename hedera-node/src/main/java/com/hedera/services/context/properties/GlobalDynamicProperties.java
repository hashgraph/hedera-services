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
import static com.hedera.services.context.properties.PropertyNames.AUTO_RENEW_GRACE_PERIOD;
import static com.hedera.services.context.properties.PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE;
import static com.hedera.services.context.properties.PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN;
import static com.hedera.services.context.properties.PropertyNames.BALANCES_EXPORT_DIR_PATH;
import static com.hedera.services.context.properties.PropertyNames.BALANCES_EXPORT_ENABLED;
import static com.hedera.services.context.properties.PropertyNames.BALANCES_EXPORT_PERIOD_SECS;
import static com.hedera.services.context.properties.PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES;
import static com.hedera.services.context.properties.PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD;
import static com.hedera.services.context.properties.PropertyNames.CACHE_RECORDS_TTL;
import static com.hedera.services.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS;
import static com.hedera.services.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS;
import static com.hedera.services.context.properties.PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_CHAIN_ID;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_DEFAULT_LIFETIME;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.services.context.properties.PropertyNames.FEES_MIN_CONGESTION_PERIOD;
import static com.hedera.services.context.properties.PropertyNames.FEES_PERCENT_CONGESTION_MULTIPLIERS;
import static com.hedera.services.context.properties.PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER;
import static com.hedera.services.context.properties.PropertyNames.FILES_MAX_SIZE_KB;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_TXN_MAX_VALID_DURATION;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_TXN_MIN_VALID_DURATION;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_FUNDING_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TRANSFERS_MAX_LEN;
import static com.hedera.services.context.properties.PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT;
import static com.hedera.services.context.properties.PropertyNames.SCHEDULING_LONG_TERM_ENABLED;
import static com.hedera.services.context.properties.PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS;
import static com.hedera.services.context.properties.PropertyNames.SCHEDULING_MAX_TXN_PER_SEC;
import static com.hedera.services.context.properties.PropertyNames.SCHEDULING_WHITE_LIST;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_PER_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_ARE_ENABLED;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE;

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
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

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
    private byte[] chainIdBytes;
    private Bytes32 chainIdBytes32;
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
    private boolean expandSigsFromImmutableState;
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
    private long maxNumAccounts;
    private long maxNumContracts;
    private long maxNumFiles;
    private long maxNumTokens;
    private long maxNumTokenRels;
    private long maxNumTopics;
    private long maxNumSchedules;
    private boolean utilPrngEnabled;
    private Set<SidecarType> enabledSidecars;
    private boolean requireMinStakeToReward;
    private Map<Long, Long> nodeMaxMinStakeRatios;
    private int sidecarMaxSizeMb;
    private boolean enableTraceabilityMigration;

    @Inject
    public GlobalDynamicProperties(
            HederaNumbers hederaNums, @CompositeProps PropertySource properties) {
        this.hederaNums = hederaNums;
        this.properties = properties;

        reload();
    }

    public void reload() {
        maxNftMetadataBytes = properties.getIntProperty(TOKENS_NFTS_MAX_METADATA_BYTES);
        maxBatchSizeBurn = properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_BURN);
        maxBatchSizeMint = properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_MINT);
        maxBatchSizeWipe = properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE);
        maxNftQueryRange = properties.getLongProperty(TOKENS_NFTS_MAX_QUERY_RANGE);
        maxTokensPerAccount = properties.getIntProperty(TOKENS_MAX_PER_ACCOUNT);
        maxTokenRelsPerInfoQuery = properties.getIntProperty(TOKENS_MAX_RELS_PER_INFO_QUERY);
        maxTokenSymbolUtf8Bytes = properties.getIntProperty(TOKENS_MAX_SYMBOL_UTF8_BYTES);
        maxTokenNameUtf8Bytes = properties.getIntProperty(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES);
        maxFileSizeKb = properties.getIntProperty(FILES_MAX_SIZE_KB);
        fundingAccount =
                AccountID.newBuilder()
                        .setShardNum(hederaNums.shard())
                        .setRealmNum(hederaNums.realm())
                        .setAccountNum(properties.getLongProperty(LEDGER_FUNDING_ACCOUNT))
                        .build();
        cacheRecordsTtl = properties.getIntProperty(CACHE_RECORDS_TTL);
        ratesIntradayChangeLimitPercent =
                properties.getIntProperty(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT);
        balancesExportPeriodSecs = properties.getIntProperty(BALANCES_EXPORT_PERIOD_SECS);
        shouldExportBalances = properties.getBooleanProperty(BALANCES_EXPORT_ENABLED);
        nodeBalanceWarningThreshold =
                properties.getLongProperty(BALANCES_NODE_BALANCE_WARN_THRESHOLD);
        pathToBalancesExportDir = properties.getStringProperty(BALANCES_EXPORT_DIR_PATH);
        shouldExportTokenBalances = properties.getBooleanProperty(BALANCES_EXPORT_TOKEN_BALANCES);
        maxTransfersLen = properties.getIntProperty(LEDGER_TRANSFERS_MAX_LEN);
        maxTokenTransfersLen = properties.getIntProperty(LEDGER_TOKEN_TRANSFERS_MAX_LEN);
        maxNftTransfersLen = properties.getIntProperty(LEDGER_NFT_TRANSFERS_MAX_LEN);
        maxMemoUtf8Bytes = properties.getIntProperty(HEDERA_TXN_MAX_MEMO_UTF8_BYTES);
        maxTxnDuration = properties.getLongProperty(HEDERA_TXN_MAX_VALID_DURATION);
        minTxnDuration = properties.getLongProperty(HEDERA_TXN_MIN_VALID_DURATION);
        minValidityBuffer = properties.getIntProperty(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS);
        maxGasPerSec = properties.getLongProperty(CONTRACTS_MAX_GAS_PER_SEC);
        final var chainId = properties.getIntProperty(CONTRACTS_CHAIN_ID);
        chainIdBytes = Integers.toBytes(chainId);
        chainIdBytes32 = Bytes32.leftPad(Bytes.of(chainIdBytes));
        defaultContractLifetime = properties.getLongProperty(CONTRACTS_DEFAULT_LIFETIME);
        feesTokenTransferUsageMultiplier =
                properties.getIntProperty(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER);
        autoRenewNumberOfEntitiesToScan =
                properties.getIntProperty(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN);
        autoRenewMaxNumberOfEntitiesToRenewOrDelete =
                properties.getIntProperty(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE);
        autoRenewGracePeriod = properties.getLongProperty(AUTO_RENEW_GRACE_PERIOD);
        maxAutoRenewDuration = properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION);
        minAutoRenewDuration = properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION);
        grpcMinAutoRenewDuration = Duration.newBuilder().setSeconds(minAutoRenewDuration).build();
        localCallEstRetBytes = properties.getIntProperty(CONTRACTS_LOCAL_CALL_EST_RET_BYTES);
        scheduledTxExpiryTimeSecs = properties.getIntProperty(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS);
        schedulingLongTermEnabled = properties.getBooleanProperty(SCHEDULING_LONG_TERM_ENABLED);
        schedulingMaxTxnPerSecond = properties.getLongProperty(SCHEDULING_MAX_TXN_PER_SEC);
        schedulingMaxExpirationFutureSeconds =
                properties.getLongProperty(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS);
        schedulingWhitelist = properties.getFunctionsProperty(SCHEDULING_WHITE_LIST);
        messageMaxBytesAllowed = properties.getIntProperty(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED);
        maxPrecedingRecords = properties.getLongProperty(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS);
        maxFollowingRecords = properties.getLongProperty(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS);
        congestionMultipliers =
                properties.getCongestionMultiplierProperty(FEES_PERCENT_CONGESTION_MULTIPLIERS);
        feesMinCongestionPeriod = properties.getIntProperty(FEES_MIN_CONGESTION_PERIOD);
        maxCustomFeesAllowed = properties.getIntProperty(TOKENS_MAX_CUSTOM_FEES_ALLOWED);
        areNftsEnabled = properties.getBooleanProperty(TOKENS_NFTS_ARE_ENABLED);
        maxNftMints = properties.getLongProperty(TOKENS_NFTS_MAX_ALLOWED_MINTS);
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
        expandSigsFromImmutableState =
                properties.getBooleanProperty("sigs.expandFromImmutableState");
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
        recordFileVersion = properties.getIntProperty(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION);
        recordSignatureFileVersion =
                properties.getIntProperty(HEDERA_RECORD_STREAM_SIG_FILE_VERSION);
        maxNumAccounts = properties.getLongProperty("accounts.maxNumber");
        maxNumContracts = properties.getLongProperty("contracts.maxNumber");
        maxNumFiles = properties.getLongProperty("files.maxNumber");
        maxNumSchedules = properties.getLongProperty("scheduling.maxNumber");
        maxNumTokens = properties.getLongProperty("tokens.maxNumber");
        maxNumTokenRels = properties.getLongProperty("tokens.maxAggregateRels");
        maxNumTopics = properties.getLongProperty("topics.maxNumber");
        utilPrngEnabled = properties.getBooleanProperty("utilPrng.isEnabled");
        requireMinStakeToReward = properties.getBooleanProperty("staking.requireMinStakeToReward");
        nodeMaxMinStakeRatios =
                properties.getNodeStakeRatiosProperty("staking.nodeMaxToMinStakeRatios");
        sidecarMaxSizeMb = properties.getIntProperty(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB);
        enableTraceabilityMigration =
                properties.getBooleanProperty(HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION);
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

    public byte[] chainIdBytes() {
        return chainIdBytes;
    }

    public Bytes32 chainIdBytes32() {
        return chainIdBytes32;
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

    public boolean expandSigsFromImmutableState() {
        return expandSigsFromImmutableState;
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

    public long maxNumAccounts() {
        return maxNumAccounts;
    }

    public long maxNumContracts() {
        return maxNumContracts;
    }

    public long maxNumFiles() {
        return maxNumFiles;
    }

    public long maxNumTokens() {
        return maxNumTokens;
    }

    public long maxNumTopics() {
        return maxNumTopics;
    }

    public long maxNumSchedules() {
        return maxNumSchedules;
    }

    public boolean isUtilPrngEnabled() {
        return utilPrngEnabled;
    }

    public long maxNumTokenRels() {
        return maxNumTokenRels;
    }

    public Set<SidecarType> enabledSidecars() {
        return enabledSidecars;
    }

    public boolean requireMinStakeToReward() {
        return requireMinStakeToReward;
    }

    public Map<Long, Long> nodeMaxMinStakeRatios() {
        return nodeMaxMinStakeRatios;
    }

    public int getSidecarMaxSizeMb() {
        return sidecarMaxSizeMb;
    }

    public boolean isTraceabilityMigrationEnabled() {
        return enableTraceabilityMigration;
    }
}
