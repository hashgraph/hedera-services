/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.service.evm.contracts.operations.HederaEvmOperationsUtilV038.EVM_VERSION_0_46;
import static com.hedera.node.app.service.mono.context.properties.EntityType.ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.EntityType.CONTRACT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_RELEASE_ALIAS_AFTER_DELETION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_CREATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_GRACE_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_TARGET_TYPES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_COMPRESS_ON_CREATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_DIR_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_PERIOD_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CACHE_CRYPTO_TRANSFER_WARM_THREADS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CACHE_RECORDS_TTL;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_AUTO_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_CREATE2;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_CHAIN_ID;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_DEFAULT_LIFETIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ENFORCE_CREATION_THROTTLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_EVM_ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_EVM_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_FREE_STORAGE_TIER_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_GRANDFATHER_CONTRACTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_KEYS_LEGACY_ACTIVATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_KNOWN_BLOCK_HASH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_KV_PAIRS_AGGREGATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_NONCES_EXTERNALIZATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PERMITTED_DELEGATE_CALLERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HRC_FACADE_ASSOCIATE_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_REFERENCE_SLOT_LIFETIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SIDECARS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SIDECAR_VALIDATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_STORAGE_SLOT_PRICE_TIERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_THROTTLE_THROTTLE_BY_GAS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACT_UNLIMITED_AUTO_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CRYPTO_CREATE_WITH_ALIAS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_LIMIT_TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_MIN_CONGESTION_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_PERCENT_CONGESTION_MULTIPLIERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_PERCENT_UTILIZATION_SCALE_FACTORS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_MAX_SIZE_KB;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_MAX_TXN_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_EIP2930_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MAX_VALID_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MIN_VALID_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LAZY_CREATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_CHANGE_HIST_MEM_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_FUNDING_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_MAX_AUTO_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_XFER_BAL_CHANGES_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_LONG_TERM_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_TXN_PER_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_WHITE_LIST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SIGS_EXPAND_FROM_IMMUTABLE_STATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_MAX_STAKE_REWARDED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_PER_HBAR_REWARD_RATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REQUIRE_MIN_STAKE_TO_REWARD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_BALANCE_THRESHOLD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_START_THRESH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_SUM_OF_CONSENSUS_WEIGHTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_AUTO_CREATIONS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_AGGREGATE_RELS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_CUSTOM_FEE_DEPTH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_PER_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_ARE_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_USE_TREASURY_WILDCARDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKEN_BALANCES_ENABLED_IN_QUERIES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOPICS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.UPGRADE_ARTIFACTS_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.UTIL_PRNG_IS_ENABLED;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.THREE_MONTHS_IN_SECONDS;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.hedera.node.app.service.mono.fees.charging.ContractStoragePriceTiers;
import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class GlobalDynamicProperties implements EvmProperties {

    private final HederaNumbers hederaNums;
    private final PropertySource properties;

    private int maxNftMetadataBytes;
    private int maxBatchSizeBurn;
    private int maxBatchSizeMint;
    private int maxNftTransfersLen;
    private int maxBatchSizeWipe;
    private long maxNftQueryRange;
    private boolean allowTreasuryToOwnNfts;
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
    private Address fundingAccountAddress;
    private int maxTransfersLen;
    private int maxTokenTransfersLen;
    private int maxMemoUtf8Bytes;
    private long maxTxnDuration;
    private long minTxnDuration;
    private int minValidityBuffer;
    private boolean eip2930Enabled;
    private long maxGasPerSec;
    private byte[] chainIdBytes;
    private Bytes32 chainIdBytes32;
    private long defaultContractLifetime;
    private boolean allowCallsToNonContractAccounts;
    private String evmVersion;
    private boolean dynamicEvmVersion;
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
    private Set<HederaFunctionality> systemContractsWithTopLevelSigsAccess;
    private CongestionMultipliers congestionMultipliers;
    private int feesMinCongestionPeriod;
    private boolean areNftsEnabled;
    private long maxNftMints;
    private int maxXferBalanceChanges;
    private int maxCustomFeeDepth;
    private ScaleFactor nftMintScaleFactor;
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
    private Set<CustomFeeType> htsUnsupportedCustomFeeReceiverDebits;
    private boolean atomicCryptoTransferEnabled;
    private boolean enableHRCAssociate;
    private boolean enableContractsNoncesExternalization;
    private KnownBlockValues knownBlockValues;
    private long exchangeRateGasReq;
    private long stakingPerHbarRewardRate;
    private long stakingMaxStakeRewarded;
    private long stakingRewardBalanceThreshold;
    private long stakingStartThreshold;
    private int nodeRewardPercent;
    private int stakingRewardPercent;
    private boolean contractAutoAssociationsEnabled;
    private boolean stakingEnabled;
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
    private boolean sidecarValidationEnabled;
    private boolean requireMinStakeToReward;
    private Map<Long, Long> nodeMaxMinStakeRatios;
    private int sidecarMaxSizeMb;
    private boolean itemizeStorageFees;
    private ContractStoragePriceTiers storagePriceTiers;
    private boolean compressRecordFilesOnCreation;
    private boolean tokenAutoCreationsEnabled;
    private boolean compressAccountBalanceFilesOnCreation;
    private long traceabilityMaxExportsPerConsSec;
    private long traceabilityMinFreeToUsedGasThrottleRatio;
    private boolean lazyCreationEnabled;
    private boolean cryptoCreateWithAliasEnabled;
    private boolean releaseAliasAfterDeletion;
    private boolean enforceContractCreationThrottle;
    private Set<Address> permittedDelegateCallers;
    private Set<Address> grandfatherContracts;
    private EntityScaleFactors entityScaleFactors;
    private long maxNumWithHapiSigsAccess;
    private int maxAutoAssociations;
    private Set<Address> contractsWithSpecialHapiSigsAccess;
    private LegacyContractIdActivations legacyContractIdActivations;
    private int sumOfConsensusWeights;
    private int cacheWarmThreads;
    private int configVersion;
    private boolean tokenBalancesEnabledInQueries;
    private boolean unlimitedAutoAssociations;

    @Inject
    public GlobalDynamicProperties(final HederaNumbers hederaNums, @CompositeProps final PropertySource properties) {
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
        allowTreasuryToOwnNfts = properties.getBooleanProperty(TOKENS_NFTS_USE_TREASURY_WILDCARDS);
        maxTokensPerAccount = properties.getIntProperty(TOKENS_MAX_PER_ACCOUNT);
        maxTokenRelsPerInfoQuery = properties.getIntProperty(TOKENS_MAX_RELS_PER_INFO_QUERY);
        maxTokenSymbolUtf8Bytes = properties.getIntProperty(TOKENS_MAX_SYMBOL_UTF8_BYTES);
        maxTokenNameUtf8Bytes = properties.getIntProperty(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES);
        maxFileSizeKb = properties.getIntProperty(FILES_MAX_SIZE_KB);
        fundingAccount = AccountID.newBuilder()
                .setShardNum(hederaNums.shard())
                .setRealmNum(hederaNums.realm())
                .setAccountNum(properties.getLongProperty(LEDGER_FUNDING_ACCOUNT))
                .build();
        fundingAccountAddress = EntityIdUtils.asTypedEvmAddress(fundingAccount);
        cacheRecordsTtl = properties.getIntProperty(CACHE_RECORDS_TTL);
        ratesIntradayChangeLimitPercent = properties.getIntProperty(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT);
        balancesExportPeriodSecs = properties.getIntProperty(BALANCES_EXPORT_PERIOD_SECS);
        shouldExportBalances = properties.getBooleanProperty(BALANCES_EXPORT_ENABLED);
        nodeBalanceWarningThreshold = properties.getLongProperty(BALANCES_NODE_BALANCE_WARN_THRESHOLD);
        pathToBalancesExportDir = properties.getStringProperty(BALANCES_EXPORT_DIR_PATH);
        shouldExportTokenBalances = properties.getBooleanProperty(BALANCES_EXPORT_TOKEN_BALANCES);
        maxTransfersLen = properties.getIntProperty(LEDGER_TRANSFERS_MAX_LEN);
        maxTokenTransfersLen = properties.getIntProperty(LEDGER_TOKEN_TRANSFERS_MAX_LEN);
        maxNftTransfersLen = properties.getIntProperty(LEDGER_NFT_TRANSFERS_MAX_LEN);
        maxMemoUtf8Bytes = properties.getIntProperty(HEDERA_TXN_MAX_MEMO_UTF8_BYTES);
        maxTxnDuration = properties.getLongProperty(HEDERA_TXN_MAX_VALID_DURATION);
        minTxnDuration = properties.getLongProperty(HEDERA_TXN_MIN_VALID_DURATION);
        minValidityBuffer = properties.getIntProperty(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS);
        eip2930Enabled = properties.getBooleanProperty(HEDERA_TXN_EIP2930_ENABLED);
        maxGasPerSec = properties.getLongProperty(CONTRACTS_MAX_GAS_PER_SEC);
        final var chainId = properties.getIntProperty(CONTRACTS_CHAIN_ID);
        chainIdBytes = Integers.toBytes(chainId);
        chainIdBytes32 = Bytes32.leftPad(Bytes.of(chainIdBytes));
        defaultContractLifetime = properties.getLongProperty(CONTRACTS_DEFAULT_LIFETIME);
        allowCallsToNonContractAccounts =
                properties.getBooleanProperty(CONTRACTS_EVM_ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS);
        dynamicEvmVersion = properties.getBooleanProperty(CONTRACTS_DYNAMIC_EVM_VERSION);
        evmVersion = properties.getStringProperty(CONTRACTS_EVM_VERSION);
        feesTokenTransferUsageMultiplier = properties.getIntProperty(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER);
        autoRenewNumberOfEntitiesToScan = properties.getIntProperty(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN);
        autoRenewMaxNumberOfEntitiesToRenewOrDelete =
                properties.getIntProperty(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE);
        autoRenewGracePeriod = properties.getLongProperty(AUTO_RENEW_GRACE_PERIOD);
        maxAutoRenewDuration = properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION);
        minAutoRenewDuration = properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION);
        grpcMinAutoRenewDuration =
                Duration.newBuilder().setSeconds(minAutoRenewDuration).build();
        localCallEstRetBytes = properties.getIntProperty(CONTRACTS_LOCAL_CALL_EST_RET_BYTES);
        scheduledTxExpiryTimeSecs = properties.getIntProperty(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS);
        schedulingLongTermEnabled = properties.getBooleanProperty(SCHEDULING_LONG_TERM_ENABLED);
        schedulingMaxTxnPerSecond = properties.getLongProperty(SCHEDULING_MAX_TXN_PER_SEC);
        schedulingMaxExpirationFutureSeconds = properties.getLongProperty(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS);
        schedulingWhitelist = properties.getFunctionsProperty(SCHEDULING_WHITE_LIST);
        systemContractsWithTopLevelSigsAccess =
                properties.getFunctionsProperty(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS);
        messageMaxBytesAllowed = properties.getIntProperty(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED);
        maxPrecedingRecords = properties.getLongProperty(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS);
        maxFollowingRecords = properties.getLongProperty(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS);
        congestionMultipliers = properties.getCongestionMultiplierProperty(FEES_PERCENT_CONGESTION_MULTIPLIERS);
        feesMinCongestionPeriod = properties.getIntProperty(FEES_MIN_CONGESTION_PERIOD);
        maxCustomFeesAllowed = properties.getIntProperty(TOKENS_MAX_CUSTOM_FEES_ALLOWED);
        areNftsEnabled = properties.getBooleanProperty(TOKENS_NFTS_ARE_ENABLED);
        maxNftMints = properties.getLongProperty(TOKENS_NFTS_MAX_ALLOWED_MINTS);
        maxXferBalanceChanges = properties.getIntProperty(LEDGER_XFER_BAL_CHANGES_MAX_LEN);
        maxCustomFeeDepth = properties.getIntProperty(TOKENS_MAX_CUSTOM_FEE_DEPTH);
        nftMintScaleFactor = properties.getThrottleScaleFactor(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR);
        upgradeArtifactsLoc = properties.getStringProperty(UPGRADE_ARTIFACTS_PATH);
        throttleByGas = properties.getBooleanProperty(CONTRACTS_THROTTLE_THROTTLE_BY_GAS);
        contractMaxRefundPercentOfGasLimit = properties.getIntProperty(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT);
        scheduleThrottleMaxGasLimit = properties.getLongProperty(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT);
        htsDefaultGasCost = properties.getLongProperty(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST);
        changeHistorianMemorySecs = properties.getIntProperty(LEDGER_CHANGE_HIST_MEM_SECS);
        autoCreationEnabled = properties.getBooleanProperty(AUTO_CREATION_ENABLED);
        expandSigsFromImmutableState = properties.getBooleanProperty(SIGS_EXPAND_FROM_IMMUTABLE_STATE);
        maxAggregateContractKvPairs = properties.getLongProperty(CONTRACTS_MAX_KV_PAIRS_AGGREGATE);
        maxIndividualContractKvPairs = properties.getIntProperty(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL);
        maxMostRecentQueryableRecords = properties.getIntProperty(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT);
        maxAllowanceLimitPerTransaction = properties.getIntProperty(HEDERA_ALLOWANCES_MAX_TXN_LIMIT);
        maxAllowanceLimitPerAccount = properties.getIntProperty(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT);
        exportPrecompileResults = properties.getBooleanProperty(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS);
        create2Enabled = properties.getBooleanProperty(CONTRACTS_ALLOW_CREATE2);
        redirectTokenCalls = properties.getBooleanProperty(CONTRACTS_REDIRECT_TOKEN_CALLS);
        enabledSidecars = properties.getSidecarsProperty(CONTRACTS_SIDECARS);
        sidecarValidationEnabled = properties.getBooleanProperty(CONTRACTS_SIDECAR_VALIDATION_ENABLED);
        enableAllowances = properties.getBooleanProperty(HEDERA_ALLOWANCES_IS_ENABLED);
        final var autoRenewTargetTypes = properties.getTypesProperty(AUTO_RENEW_TARGET_TYPES);
        expireAccounts = autoRenewTargetTypes.contains(ACCOUNT);
        expireContracts = autoRenewTargetTypes.contains(CONTRACT);
        atLeastOneAutoRenewTargetType = !autoRenewTargetTypes.isEmpty();
        limitTokenAssociations = properties.getBooleanProperty(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS);
        enableHTSPrecompileCreate = properties.getBooleanProperty(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE);
        htsUnsupportedCustomFeeReceiverDebits =
                properties.getCustomFeesProperty(CONTRACTS_PRECOMPILE_HTS_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS);
        atomicCryptoTransferEnabled =
                properties.getBooleanProperty(CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED);
        enableHRCAssociate = properties.getBooleanProperty(CONTRACTS_PRECOMPILE_HRC_FACADE_ASSOCIATE_ENABLED);
        enableContractsNoncesExternalization = properties.getBooleanProperty(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED);
        knownBlockValues = properties.getBlockValuesProperty(CONTRACTS_KNOWN_BLOCK_HASH);
        exchangeRateGasReq = properties.getLongProperty(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST);
        stakingMaxStakeRewarded = properties.getLongProperty(STAKING_MAX_STAKE_REWARDED);
        stakingRewardBalanceThreshold = properties.getLongProperty(STAKING_REWARD_BALANCE_THRESHOLD);
        stakingPerHbarRewardRate = properties.getLongProperty(STAKING_PER_HBAR_REWARD_RATE);
        stakingStartThreshold = properties.getLongProperty(STAKING_START_THRESH);
        nodeRewardPercent = properties.getIntProperty(STAKING_FEES_NODE_REWARD_PERCENT);
        stakingRewardPercent = properties.getIntProperty(STAKING_FEES_STAKING_REWARD_PERCENT);
        contractAutoAssociationsEnabled = properties.getBooleanProperty(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS);
        stakingEnabled = properties.getBooleanProperty(STAKING_IS_ENABLED);
        recordFileVersion = properties.getIntProperty(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION);
        recordSignatureFileVersion = properties.getIntProperty(HEDERA_RECORD_STREAM_SIG_FILE_VERSION);
        maxNumAccounts = properties.getLongProperty(ACCOUNTS_MAX_NUM);
        maxNumContracts = properties.getLongProperty(CONTRACTS_MAX_NUM);
        maxNumFiles = properties.getLongProperty(FILES_MAX_NUM);
        maxNumSchedules = properties.getLongProperty(SCHEDULING_MAX_NUM);
        maxNumTokens = properties.getLongProperty(TOKENS_MAX_NUM);
        maxNumTokenRels = properties.getLongProperty(TOKENS_MAX_AGGREGATE_RELS);
        maxNumTopics = properties.getLongProperty(TOPICS_MAX_NUM);
        utilPrngEnabled = properties.getBooleanProperty(UTIL_PRNG_IS_ENABLED);
        requireMinStakeToReward = properties.getBooleanProperty(STAKING_REQUIRE_MIN_STAKE_TO_REWARD);
        nodeMaxMinStakeRatios = properties.getNodeStakeRatiosProperty(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS);
        sidecarMaxSizeMb = properties.getIntProperty(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB);
        storagePriceTiers = ContractStoragePriceTiers.from(
                properties.getStringProperty(CONTRACTS_STORAGE_SLOT_PRICE_TIERS),
                properties.getIntProperty(CONTRACTS_FREE_STORAGE_TIER_LIMIT),
                maxAggregateContractKvPairs,
                properties.getLongProperty(CONTRACTS_REFERENCE_SLOT_LIFETIME));
        itemizeStorageFees = properties.getBooleanProperty(CONTRACTS_ITEMIZE_STORAGE_FEES);
        compressRecordFilesOnCreation = properties.getBooleanProperty(HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION);
        tokenAutoCreationsEnabled = properties.getBooleanProperty(TOKENS_AUTO_CREATIONS_ENABLED);
        compressAccountBalanceFilesOnCreation = properties.getBooleanProperty(BALANCES_COMPRESS_ON_CREATION);
        traceabilityMaxExportsPerConsSec = properties.getLongProperty(TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC);
        traceabilityMinFreeToUsedGasThrottleRatio =
                properties.getLongProperty(TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO);
        lazyCreationEnabled = properties.getBooleanProperty(LAZY_CREATION_ENABLED);
        cryptoCreateWithAliasEnabled = properties.getBooleanProperty(CRYPTO_CREATE_WITH_ALIAS_ENABLED);
        releaseAliasAfterDeletion = properties.getBooleanProperty(ACCOUNTS_RELEASE_ALIAS_AFTER_DELETION);
        enforceContractCreationThrottle = properties.getBooleanProperty(CONTRACTS_ENFORCE_CREATION_THROTTLE);
        entityScaleFactors = properties.getEntityScaleFactorsProperty(FEES_PERCENT_UTILIZATION_SCALE_FACTORS);
        permittedDelegateCallers = properties.getEvmAddresses(CONTRACTS_PERMITTED_DELEGATE_CALLERS);
        grandfatherContracts = properties.getEvmAddresses(CONTRACTS_GRANDFATHER_CONTRACTS);
        legacyContractIdActivations = properties.getLegacyActivationsProperty(CONTRACTS_KEYS_LEGACY_ACTIVATIONS);
        contractsWithSpecialHapiSigsAccess = properties.getEvmAddresses(CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS);
        maxNumWithHapiSigsAccess = properties.getLongProperty(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS);
        maxAutoAssociations = properties.getIntProperty(LEDGER_MAX_AUTO_ASSOCIATIONS);
        sumOfConsensusWeights = properties.getIntProperty(STAKING_SUM_OF_CONSENSUS_WEIGHTS);
        cacheWarmThreads = properties.getIntProperty(CACHE_CRYPTO_TRANSFER_WARM_THREADS);
        tokenBalancesEnabledInQueries = properties.getBooleanProperty(TOKEN_BALANCES_ENABLED_IN_QUERIES);
        unlimitedAutoAssociations = properties.getBooleanProperty(CONTRACT_UNLIMITED_AUTO_ASSOCIATIONS);
    }

    public int sumOfConsensusWeights() {
        return sumOfConsensusWeights;
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

    public boolean treasuryNftAllowance() {
        return allowTreasuryToOwnNfts;
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

    public Address fundingAccountAddress() {
        return fundingAccountAddress;
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

    public boolean isEip2930Enabled() {
        return eip2930Enabled;
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

    public boolean allowCallsToNonContractAccounts() {
        return allowCallsToNonContractAccounts;
    }

    public String evmVersion() {
        return evmVersion;
    }

    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
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

    public ScaleFactor nftMintScaleFactor() {
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

    public Set<CustomFeeType> getHtsUnsupportedCustomFeeReceiverDebits() {
        return htsUnsupportedCustomFeeReceiverDebits;
    }

    public boolean isAtomicCryptoTransferEnabled() {
        return atomicCryptoTransferEnabled;
    }

    public boolean isHRCAssociateEnabled() {
        return enableHRCAssociate;
    }

    public boolean isContractsNoncesExternalizationEnabled() {
        return enableContractsNoncesExternalization;
    }

    public KnownBlockValues knownBlockValues() {
        return knownBlockValues;
    }

    public long exchangeRateGasReq() {
        return exchangeRateGasReq;
    }

    public long stakingPerHbarRewardRate() {
        return stakingPerHbarRewardRate;
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

    public boolean validateSidecarsEnabled() {
        return sidecarValidationEnabled;
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

    public ContractStoragePriceTiers storagePriceTiers() {
        return storagePriceTiers;
    }

    public boolean shouldItemizeStorageFees() {
        return itemizeStorageFees;
    }

    public boolean shouldCompressRecordFilesOnCreation() {
        return compressRecordFilesOnCreation;
    }

    public boolean areTokenAutoCreationsEnabled() {
        return tokenAutoCreationsEnabled;
    }

    public boolean shouldCompressAccountBalanceFilesOnCreation() {
        return compressAccountBalanceFilesOnCreation;
    }

    public long traceabilityMinFreeToUsedGasThrottleRatio() {
        return traceabilityMinFreeToUsedGasThrottleRatio;
    }

    public long traceabilityMaxExportsPerConsSec() {
        return traceabilityMaxExportsPerConsSec;
    }

    public boolean isLazyCreationEnabled() {
        return lazyCreationEnabled;
    }

    public boolean isCryptoCreateWithAliasEnabled() {
        return cryptoCreateWithAliasEnabled;
    }

    public boolean releaseAliasAfterDeletion() {
        return releaseAliasAfterDeletion;
    }

    public EntityScaleFactors entityScaleFactors() {
        return entityScaleFactors;
    }

    public boolean shouldEnforceAccountCreationThrottleForContracts() {
        return enforceContractCreationThrottle;
    }

    public LegacyContractIdActivations legacyContractIdActivations() {
        return legacyContractIdActivations;
    }

    public boolean isImplicitCreationEnabled() {
        return autoCreationEnabled && lazyCreationEnabled;
    }

    public Set<Address> permittedDelegateCallers() {
        return permittedDelegateCallers;
    }

    public Set<Address> grandfatherContracts() {
        return grandfatherContracts;
    }

    public Set<HederaFunctionality> systemContractsWithTopLevelSigsAccess() {
        return systemContractsWithTopLevelSigsAccess;
    }

    public long maxNumWithHapiSigsAccess() {
        return maxNumWithHapiSigsAccess;
    }

    public Set<Address> contractsWithSpecialHapiSigsAccess() {
        return contractsWithSpecialHapiSigsAccess;
    }

    public int maxAllowedAutoAssociations() {
        return maxAutoAssociations;
    }

    public long explicitAutoAssocSlotLifetime() {
        // If account auto-renew is disabled we use the SDK default auto-renew period for slot lifetime
        return expireAccounts ? 0 : THREE_MONTHS_IN_SECONDS;
    }

    public int cacheCryptoTransferWarmThreads() {
        return cacheWarmThreads;
    }

    /**
     * The maximum amount of stake that can be rewarded at the full reward rate (in tinybars). If twice this
     * amount were staked for reward, then the effective reward rate would be cut in half.
     *
     * @return the maximum amount of stake that can be rewarded at the full reward rate
     */
    public long maxStakeRewarded() {
        return stakingMaxStakeRewarded;
    }

    /**
     * The reward threshold balance of account {@code 0.0.800} (in tinybars). When the balance is this or more,
     * then the reward rate set in the {@code staking.perHbarRewardRate} property is used.  When the balance is less
     * than this threshold, then a fraction of that reward rate is used.
     *
     * @return the minimum balance of account {@code 0.0.800} required for the full reward rate to be used
     */
    public long stakingRewardBalanceThreshold() {
        return stakingRewardBalanceThreshold;
    }

    public boolean areTokenBalancesEnabledInQueries() {
        return tokenBalancesEnabledInQueries;
    }

    public boolean callsToNonExistingEntitiesEnabled(Address target) {
        return !(!evmVersion.equals(EVM_VERSION_0_46)
                || !allowCallsToNonContractAccounts
                || grandfatherContracts.contains(target));
    }

    public boolean areAutoAssociationsUnlimited() {
        return unlimitedAutoAssociations;
    }
}
