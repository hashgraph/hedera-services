/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

public class PropertyNames {
    private PropertyNames() {
        /* No-Op */
    }

    /* ---- Bootstrap properties ---- */
    public static final String BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE =
            "bootstrap.feeSchedulesJson.resource";
    public static final String BOOTSTRAP_GENESIS_PUBLIC_KEY = "bootstrap.genesisPublicKey";
    public static final String BOOTSTRAP_HAPI_PERMISSIONS_PATH = "bootstrap.hapiPermissions.path";
    public static final String BOOTSTRAP_NETWORK_PROPERTIES_PATH =
            "bootstrap.networkProperties.path";
    public static final String BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV =
            "bootstrap.rates.currentHbarEquiv";
    public static final String BOOTSTRAP_RATES_CURRENT_CENT_EQUIV =
            "bootstrap.rates.currentCentEquiv";
    public static final String BOOTSTRAP_RATES_CURRENT_EXPIRY = "bootstrap.rates.currentExpiry";
    public static final String BOOTSTRAP_RATES_NEXT_HBAR_EQUIV = "bootstrap.rates.nextHbarEquiv";
    public static final String BOOTSTRAP_RATES_NEXT_CENT_EQUIV = "bootstrap.rates.nextCentEquiv";
    public static final String BOOTSTRAP_RATES_NEXT_EXPIRY = "bootstrap.rates.nextExpiry";
    public static final String BOOTSTRAP_SYSTEM_ENTITY_EXPIRY = "bootstrap.system.entityExpiry";
    public static final String BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE =
            "bootstrap.throttleDefsJson.resource";

    /* ---- Global Static properties ---- */
    public static final String ACCOUNTS_ADDRESS_BOOK_ADMIN = "accounts.addressBookAdmin";
    public static final String ACCOUNTS_EXCHANGE_RATES_ADMIN = "accounts.exchangeRatesAdmin";
    public static final String ACCOUNTS_FEE_SCHEDULE_ADMIN = "accounts.feeSchedulesAdmin";
    public static final String ACCOUNTS_FREEZE_ADMIN = "accounts.freezeAdmin";
    public static final String ACCOUNTS_LAST_THROTTLE_EXEMPT = "accounts.lastThrottleExempt";
    public static final String ACCOUNTS_NODE_REWARD_ACCOUNT = "accounts.nodeRewardAccount";
    public static final String ACCOUNTS_STAKING_REWARD_ACCOUNT = "accounts.stakingRewardAccount";
    public static final String ACCOUNTS_SYSTEM_ADMIN = "accounts.systemAdmin";
    public static final String ACCOUNTS_SYSTEM_DELETE_ADMIN = "accounts.systemDeleteAdmin";
    public static final String ACCOUNTS_SYSTEM_UNDELETE_ADMIN = "accounts.systemUndeleteAdmin";
    public static final String ACCOUNTS_TREASURY = "accounts.treasury";
    public static final String ENTITIES_MAX_LIFETIME = "entities.maxLifetime";
    public static final String ENTITIES_SYSTEM_DELETABLE = "entities.systemDeletable";
    public static final String FILES_ADDRESS_BOOK = "files.addressBook";
    public static final String FILES_NETWORK_PROPERTIES = "files.networkProperties";
    public static final String FILES_EXCHANGE_RATES = "files.exchangeRates";
    public static final String FILES_FEE_SCHEDULES = "files.feeSchedules";
    public static final String FILES_HAPI_PERMISSIONS = "files.hapiPermissions";
    public static final String FILES_NODE_DETAILS = "files.nodeDetails";
    public static final String FILES_SOFTWARE_UPDATE_RANGE = "files.softwareUpdateRange";
    public static final String FILES_THROTTLE_DEFINITIONS = "files.throttleDefinitions";
    public static final String HEDERA_FIRST_USER_ENTITY = "hedera.firstUserEntity";
    public static final String HEDERA_REALM = "hedera.realm";
    public static final String HEDERA_SHARD = "hedera.shard";
    public static final String LEDGER_NUM_SYSTEM_ACCOUNTS = "ledger.numSystemAccounts";
    public static final String LEDGER_TOTAL_TINY_BAR_FLOAT = "ledger.totalTinyBarFloat";
    public static final String LEDGER_ID = "ledger.id";
    public static final String STAKING_PERIOD_MINS = "staking.periodMins";
    public static final String STAKING_REWARD_HISTORY_NUM_STORED_PERIODS =
            "staking.rewardHistory.numStoredPeriods";

    /* ---- Global dynamic properties ---- */
    public static final String ACCOUNTS_MAX_NUM = "accounts.maxNumber";
    public static final String AUTO_CREATION_ENABLED = "autoCreation.enabled";
    public static final String BALANCES_EXPORT_DIR_PATH = "balances.exportDir.path";
    public static final String BALANCES_EXPORT_ENABLED = "balances.exportEnabled";
    public static final String BALANCES_EXPORT_PERIOD_SECS = "balances.exportPeriodSecs";
    public static final String BALANCES_EXPORT_TOKEN_BALANCES = "balances.exportTokenBalances";
    public static final String BALANCES_NODE_BALANCE_WARN_THRESHOLD =
            "balances.nodeBalanceWarningThreshold";
    public static final String CACHE_RECORDS_TTL = "cache.records.ttl";
    public static final String CONTRACTS_ITEMIZE_STORAGE_FEES = "contracts.itemizeStorageFees";
    public static final String CONTRACTS_REFERENCE_SLOT_LIFETIME =
            "contracts.referenceSlotLifetime";
    public static final String CONTRACTS_FREE_STORAGE_TIER_LIMIT = "contracts.freeStorageTierLimit";
    public static final String CONTRACTS_STORAGE_SLOT_PRICE_TIERS =
            "contract.storageSlotPriceTiers";
    public static final String CONTRACTS_DEFAULT_LIFETIME = "contracts.defaultLifetime";
    public static final String CONTRACTS_KNOWN_BLOCK_HASH = "contracts.knownBlockHash";
    public static final String CONTRACTS_LOCAL_CALL_EST_RET_BYTES =
            "contracts.localCall.estRetBytes";
    public static final String CONTRACTS_ALLOW_CREATE2 = "contracts.allowCreate2";
    public static final String CONTRACTS_ALLOW_AUTO_ASSOCIATIONS =
            "contracts.allowAutoAssociations";
    public static final String CONTRACTS_MAX_GAS_PER_SEC = "contracts.maxGasPerSec";
    public static final String CONTRACTS_MAX_KV_PAIRS_AGGREGATE = "contracts.maxKvPairs.aggregate";
    public static final String CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL =
            "contracts.maxKvPairs.individual";
    public static final String CONTRACTS_MAX_NUM = "contracts.maxNumber";
    public static final String CONTRACTS_CHAIN_ID = "contracts.chainId";
    public static final String CONTRACTS_SIDECARS = "contracts.sidecars";
    public static final String CONTRACTS_THROTTLE_THROTTLE_BY_GAS =
            "contracts.throttle.throttleByGas";
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT =
            "contracts.maxRefundPercentOfGasLimit";
    public static final String CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT =
            "contracts.scheduleThrottleMaxGasLimit";
    public static final String CONTRACTS_REDIRECT_TOKEN_CALLS = "contracts.redirectTokenCalls";
    public static final String CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST =
            "contracts.precompile.exchangeRateGasCost";
    public static final String CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST =
            "contracts.precompile.htsDefaultGasCost";
    public static final String CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS =
            "contracts.precompile.exportRecordResults";
    public static final String CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE =
            "contracts.precompile.htsEnableTokenCreate";
    public static final String FILES_MAX_NUM = "files.maxNumber";
    public static final String FILES_MAX_SIZE_KB = "files.maxSizeKb";
    public static final String HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB =
            "hedera.recordStream.sidecarMaxSizeMb";
    public static final String FEES_MIN_CONGESTION_PERIOD = "fees.minCongestionPeriod";
    public static final String FEES_PERCENT_CONGESTION_MULTIPLIERS =
            "fees.percentCongestionMultipliers";
    public static final String FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER =
            "fees.tokenTransferUsageMultiplier";
    public static final String HEDERA_TXN_MAX_MEMO_UTF8_BYTES =
            "hedera.transaction.maxMemoUtf8Bytes";
    public static final String HEDERA_TXN_MAX_VALID_DURATION =
            "hedera.transaction.maxValidDuration";
    public static final String HEDERA_TXN_MIN_VALID_DURATION =
            "hedera.transaction.minValidDuration";
    public static final String HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS =
            "hedera.transaction.minValidityBufferSecs";
    public static final String HEDERA_RECORD_STREAM_RECORD_FILE_VERSION =
            "hedera.recordStream.recordFileVersion";
    public static final String HEDERA_RECORD_STREAM_SIG_FILE_VERSION =
            "hedera.recordStream.signatureFileVersion";
    public static final String AUTO_RENEW_TARGET_TYPES = "autoRenew.targetTypes";
    public static final String AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN =
            "autorenew.numberOfEntitiesToScan";
    public static final String AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE =
            "autorenew.maxNumberOfEntitiesToRenewOrDelete";
    public static final String EXPIRY_THROTTLE_RESOURCE = "expiry.throttleResource";
    public static final String AUTO_RENEW_GRACE_PERIOD = "autorenew.gracePeriod";
    public static final String LEDGER_CHANGE_HIST_MEM_SECS = "ledger.changeHistorian.memorySecs";
    public static final String LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION =
            "ledger.autoRenewPeriod.maxDuration";
    public static final String LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION =
            "ledger.autoRenewPeriod.minDuration";
    public static final String LEDGER_XFER_BAL_CHANGES_MAX_LEN = "ledger.xferBalanceChanges.maxLen";
    public static final String LEDGER_FUNDING_ACCOUNT = "ledger.fundingAccount";
    public static final String LEDGER_TRANSFERS_MAX_LEN = "ledger.transfers.maxLen";
    public static final String LEDGER_TOKEN_TRANSFERS_MAX_LEN = "ledger.tokenTransfers.maxLen";
    public static final String LEDGER_NFT_TRANSFERS_MAX_LEN = "ledger.nftTransfers.maxLen";
    public static final String LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT =
            "ledger.records.maxQueryableByAccount";
    public static final String LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS =
            "ledger.schedule.txExpiryTimeSecs";
    public static final String RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT =
            "rates.intradayChangeLimitPercent";
    public static final String RATES_MIDNIGHT_CHECK_INTERVAL = "rates.midnightCheckInterval";
    public static final String SCHEDULING_LONG_TERM_ENABLED = "scheduling.longTermEnabled";
    public static final String SCHEDULING_MAX_TXN_PER_SEC = "scheduling.maxTxnPerSecond";
    public static final String SCHEDULING_MAX_NUM = "scheduling.maxNumber";
    public static final String SCHEDULING_MAX_EXPIRATION_FUTURE_SECS =
            "scheduling.maxExpirationFutureSeconds";
    public static final String SCHEDULING_WHITE_LIST = "scheduling.whitelist";
    public static final String SIGS_EXPAND_FROM_IMMUTABLE_STATE = "sigs.expandFromImmutableState";
    public static final String STAKING_FEES_NODE_REWARD_PERCENT =
            "staking.fees.nodeRewardPercentage";
    public static final String STAKING_FEES_STAKING_REWARD_PERCENT =
            "staking.fees.stakingRewardPercentage";
    public static final String STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS =
            "staking.nodeMaxToMinStakeRatios";
    public static final String STAKING_IS_ENABLED = "staking.isEnabled";
    public static final String STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR =
            "staking.maxDailyStakeRewardThPerH";
    public static final String STAKING_REQUIRE_MIN_STAKE_TO_REWARD =
            "staking.requireMinStakeToReward";
    public static final String STAKING_REWARD_RATE = "staking.rewardRate";
    public static final String STAKING_START_THRESH = "staking.startThreshold";
    public static final String TOKENS_MAX_AGGREGATE_RELS = "tokens.maxAggregateRels";
    public static final String TOKENS_MAX_NUM = "tokens.maxNumber";
    public static final String TOKENS_MAX_RELS_PER_INFO_QUERY = "tokens.maxRelsPerInfoQuery";
    public static final String TOKENS_MAX_PER_ACCOUNT = "tokens.maxPerAccount";
    public static final String TOKENS_MAX_SYMBOL_UTF8_BYTES = "tokens.maxSymbolUtf8Bytes";
    public static final String TOKENS_MAX_TOKEN_NAME_UTF8_BYTES = "tokens.maxTokenNameUtf8Bytes";
    public static final String TOKENS_MAX_CUSTOM_FEES_ALLOWED = "tokens.maxCustomFeesAllowed";
    public static final String TOKENS_MAX_CUSTOM_FEE_DEPTH = "tokens.maxCustomFeeDepth";
    public static final String TOKENS_NFTS_ARE_ENABLED = "tokens.nfts.areEnabled";
    public static final String TOKENS_NFTS_MAX_METADATA_BYTES = "tokens.nfts.maxMetadataBytes";
    public static final String TOKENS_NFTS_MAX_BATCH_SIZE_BURN = "tokens.nfts.maxBatchSizeBurn";
    public static final String TOKENS_NFTS_MAX_BATCH_SIZE_WIPE = "tokens.nfts.maxBatchSizeWipe";
    public static final String TOKENS_NFTS_MAX_BATCH_SIZE_MINT = "tokens.nfts.maxBatchSizeMint";
    public static final String TOKENS_NFTS_MAX_ALLOWED_MINTS = "tokens.nfts.maxAllowedMints";
    public static final String TOKENS_NFTS_MAX_QUERY_RANGE = "tokens.nfts.maxQueryRange";
    public static final String TOKENS_NFTS_USE_TREASURY_WILDCARDS =
            "tokens.nfts.useTreasuryWildcards";
    public static final String TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR =
            "tokens.nfts.mintThrottleScaleFactor";
    public static final String TOKENS_NFTS_USE_VIRTUAL_MERKLE = "tokens.nfts.useVirtualMerkle";
    public static final String TOPICS_MAX_NUM = "topics.maxNumber";
    public static final String TOKENS_NFTS_USE_TREASURY_WILD_CARDS =
            "tokens.nfts.useTreasuryWildcards";
    public static final String CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED =
            "consensus.message.maxBytesAllowed";
    public static final String CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS =
            "consensus.handle.maxPrecedingRecords";
    public static final String CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS =
            "consensus.handle.maxFollowingRecords";
    public static final String UPGRADE_ARTIFACTS_PATH = "upgrade.artifacts.path";
    public static final String HEDERA_ALLOWANCES_MAX_TXN_LIMIT =
            "hedera.allowances.maxTransactionLimit";
    public static final String HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT =
            "hedera.allowances.maxAccountLimit";
    public static final String HEDERA_ALLOWANCES_IS_ENABLED = "hedera.allowances.isEnabled";
    public static final String ENTITIES_LIMIT_TOKEN_ASSOCIATIONS =
            "entities.limitTokenAssociations";
    public static final String UTIL_PRNG_IS_ENABLED = "utilPrng.isEnabled";
    public static final String HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION =
            "hedera.recordStream.enableTraceabilityMigration";
    public static final String HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION =
            "hedera.recordStream.compressFilesOnCreation";

    /* ---- Node properties ----- */
    public static final String DEV_ONLY_DEFAULT_NODE_LISTENS = "dev.onlyDefaultNodeListens";
    public static final String DEV_DEFAULT_LISTENING_NODE_ACCOUNT =
            "dev.defaultListeningNodeAccount";
    public static final String GRPC_PORT = "grpc.port";
    public static final String GRPC_TLS_PORT = "grpc.tlsPort";
    public static final String HEDERA_ACCOUNTS_EXPORT_PATH = "hedera.accountsExportPath";
    public static final String HEDERA_EXPORT_ACCOUNTS_ON_STARTUP = "hedera.exportAccountsOnStartup";
    public static final String HEDERA_PREFETCH_QUEUE_CAPACITY = "hedera.prefetch.queueCapacity";
    public static final String HEDERA_PREFETCH_THREAD_POOL_SIZE = "hedera.prefetch.threadPoolSize";
    public static final String HEDERA_PREFETCH_CODE_CACHE_TTL_SECS =
            "hedera.prefetch.codeCacheTtlSecs";
    public static final String HEDERA_PROFILES_ACTIVE = "hedera.profiles.active";
    public static final String HEDERA_RECORD_STREAM_IS_ENABLED = "hedera.recordStream.isEnabled";
    public static final String HEDERA_RECORD_STREAM_LOG_DIR = "hedera.recordStream.logDir";
    public static final String HEDERA_RECORD_STREAM_SIDE_CAR_DIR = "hedera.recordStream.sidecarDir";
    public static final String HEDERA_RECORD_STREAM_LOG_PERIOD = "hedera.recordStream.logPeriod";
    public static final String HEDERA_RECORD_STREAM_QUEUE_CAPACITY =
            "hedera.recordStream.queueCapacity";
    public static final String HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION =
            "hedera.recordStream.logEveryTransaction";
    public static final String ISS_RESET_PERIOD = "iss.resetPeriod";
    public static final String ISS_ROUNDS_TO_LOG = "iss.roundsToLog";
    public static final String NETTY_MODE = "netty.mode";
    public static final String NETTY_PROD_FLOW_CONTROL_WINDOW = "netty.prod.flowControlWindow";
    public static final String NETTY_PROD_MAX_CONCURRENT_CALLS = "netty.prod.maxConcurrentCalls";
    public static final String NETTY_PROD_MAX_CONNECTION_AGE = "netty.prod.maxConnectionAge";
    public static final String NETTY_PROD_MAX_CONNECTION_AGE_GRACE =
            "netty.prod.maxConnectionAgeGrace";
    public static final String NETTY_PROD_MAX_CONNECTION_IDLE = "netty.prod.maxConnectionIdle";
    public static final String NETTY_PROD_KEEP_ALIVE_TIME = "netty.prod.keepAliveTime";
    public static final String NETTY_PROD_KEEP_ALIVE_TIMEOUT = "netty.prod.keepAliveTimeout";
    public static final String NETTY_START_RETRIES = "netty.startRetries";
    public static final String NETTY_START_RETRY_INTERVAL_MS = "netty.startRetryIntervalMs";
    public static final String NETTY_TLS_CERT_PATH = "netty.tlsCrt.path";
    public static final String NETTY_TLS_KEY_PATH = "netty.tlsKey.path";
    public static final String QUERIES_BLOB_LOOK_UP_RETRIES = "queries.blob.lookupRetries";
    public static final String STATS_CONS_THROTTLES_TO_SAMPLE = "stats.consThrottlesToSample";
    public static final String STATS_HAPI_THROTTLES_TO_SAMPLE = "stats.hapiThrottlesToSample";
    public static final String STATS_EXECUTION_TIMES_TO_TRACK = "stats.executionTimesToTrack";
    public static final String STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS =
            "stats.entityUtils.gaugeUpdateIntervalMs";
    public static final String STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS =
            "stats.hapiOps.speedometerUpdateIntervalMs";
    public static final String STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS =
            "stats.throttleUtils.gaugeUpdateIntervalMs";
    public static final String STATS_RUNNING_AVG_HALF_LIFE_SECS = "stats.runningAvgHalfLifeSecs";
    public static final String STATS_SPEEDOMETER_HALF_LIFE_SECS = "stats.speedometerHalfLifeSecs";
}
