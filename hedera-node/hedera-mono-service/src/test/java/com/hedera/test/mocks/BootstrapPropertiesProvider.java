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
package com.hedera.test.mocks;

import static com.hedera.services.context.properties.EntityType.CONTRACT;
import static com.hedera.services.context.properties.PropertyNames.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.EntityType;
import com.hedera.services.context.properties.Profile;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttling.MapAccessType;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.List;
import java.util.Set;

public final class BootstrapPropertiesProvider {

    private BootstrapPropertiesProvider() {
        // to prevent instantiation
    }

    /**
     * Simulates loading of the {@code src/main/resources/bootstrap.properties} production file by
     * providing a mock with the same values.
     *
     * <p><b>Prefer this method over instantiating a new BootstrapProperties instance in unit
     * tests.</b> There's usually no reason to depend on BootstrapProperties' file startup mechanism
     * in unit tests when we can mock the properties.
     */
    public static BootstrapProperties mockBaseBootstrap() {
        var properties = mock(BootstrapProperties.class);

        lenient()
                .when(properties.getStringProperty(BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE))
                .thenReturn("feeSchedules.json");
        lenient()
                .when(properties.getStringProperty(BOOTSTRAP_GENESIS_PUBLIC_KEY))
                .thenReturn("0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
        lenient()
                .when(properties.getStringProperty(BOOTSTRAP_HAPI_PERMISSIONS_PATH))
                .thenReturn("../data/config/api-permission.properties");
        lenient()
                .when(properties.getStringProperty(BOOTSTRAP_NETWORK_PROPERTIES_PATH))
                .thenReturn("../data/config/application.properties");
        lenient().when(properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV)).thenReturn(1);
        lenient()
                .when(properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV))
                .thenReturn(12);
        lenient()
                .when(properties.getLongProperty(BOOTSTRAP_RATES_CURRENT_EXPIRY))
                .thenReturn(4102444800L);
        lenient().when(properties.getIntProperty(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV)).thenReturn(1);
        lenient().when(properties.getIntProperty(BOOTSTRAP_RATES_NEXT_CENT_EQUIV)).thenReturn(15);
        lenient()
                .when(properties.getLongProperty(BOOTSTRAP_RATES_NEXT_EXPIRY))
                .thenReturn(4102444800L);
        lenient()
                .when(properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY))
                .thenReturn(1812637686L);
        lenient()
                .when(properties.getStringProperty(BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE))
                .thenReturn("throttles.json");
        lenient().when(properties.getLongProperty(ACCOUNTS_ADDRESS_BOOK_ADMIN)).thenReturn(55L);
        lenient().when(properties.getLongProperty(ACCOUNTS_EXCHANGE_RATES_ADMIN)).thenReturn(57L);
        lenient().when(properties.getLongProperty(ACCOUNTS_FEE_SCHEDULE_ADMIN)).thenReturn(56L);
        lenient().when(properties.getLongProperty(ACCOUNTS_FREEZE_ADMIN)).thenReturn(58L);
        lenient().when(properties.getLongProperty(ACCOUNTS_LAST_THROTTLE_EXEMPT)).thenReturn(100L);
        lenient().when(properties.getLongProperty(ACCOUNTS_NODE_REWARD_ACCOUNT)).thenReturn(801L);
        lenient()
                .when(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT))
                .thenReturn(800L);
        lenient().when(properties.getLongProperty(ACCOUNTS_SYSTEM_ADMIN)).thenReturn(50L);
        lenient().when(properties.getLongProperty(ACCOUNTS_SYSTEM_DELETE_ADMIN)).thenReturn(59L);
        lenient().when(properties.getLongProperty(ACCOUNTS_SYSTEM_UNDELETE_ADMIN)).thenReturn(60L);
        lenient().when(properties.getLongProperty(ACCOUNTS_TREASURY)).thenReturn(2L);
        lenient()
                .when(properties.getBooleanProperty(AUTO_RENEW_GRANT_FREE_RENEWALS))
                .thenReturn(true);
        lenient().when(properties.getLongProperty(ENTITIES_MAX_LIFETIME)).thenReturn(3153600000L);
        lenient()
                .when(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .thenReturn(Set.of(EntityType.FILE));
        lenient().when(properties.getLongProperty(FILES_ADDRESS_BOOK)).thenReturn(101L);
        lenient().when(properties.getLongProperty(FILES_NETWORK_PROPERTIES)).thenReturn(121L);
        lenient().when(properties.getLongProperty(FILES_EXCHANGE_RATES)).thenReturn(112L);
        lenient().when(properties.getLongProperty(FILES_FEE_SCHEDULES)).thenReturn(111L);
        lenient().when(properties.getLongProperty(FILES_HAPI_PERMISSIONS)).thenReturn(122L);
        lenient().when(properties.getLongProperty(FILES_NODE_DETAILS)).thenReturn(102L);
        lenient()
                .when(properties.getStringProperty(FILES_SOFTWARE_UPDATE_RANGE))
                .thenReturn("150-159");
        lenient().when(properties.getLongProperty(FILES_THROTTLE_DEFINITIONS)).thenReturn(123L);
        lenient().when(properties.getLongProperty(HEDERA_FIRST_USER_ENTITY)).thenReturn(1001L);
        lenient().when(properties.getLongProperty(HEDERA_REALM)).thenReturn(0L);
        lenient().when(properties.getLongProperty(HEDERA_SHARD)).thenReturn(0L);
        lenient()
                .when(
                        properties.getBooleanProperty(
                                HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION))
                .thenReturn(false);
        lenient().when(properties.getStringProperty(LEDGER_ID)).thenReturn("0x03");
        lenient().when(properties.getIntProperty(LEDGER_NUM_SYSTEM_ACCOUNTS)).thenReturn(100);
        lenient()
                .when(properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .thenReturn(5_000_000_000_000_000_000L);
        lenient().when(properties.getLongProperty(STAKING_PERIOD_MINS)).thenReturn(1440L);
        lenient()
                .when(properties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .thenReturn(365);
        lenient().when(properties.getLongProperty(ACCOUNTS_MAX_NUM)).thenReturn(5_000_000L);
        lenient().when(properties.getBooleanProperty(AUTO_CREATION_ENABLED)).thenReturn(true);
        lenient()
                .when(properties.getBooleanProperty(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS))
                .thenReturn(false);
        lenient()
                .when(properties.getStringProperty(BALANCES_EXPORT_DIR_PATH))
                .thenReturn("../data/accountBalances/");
        lenient().when(properties.getBooleanProperty(BALANCES_EXPORT_ENABLED)).thenReturn(true);
        lenient().when(properties.getIntProperty(BALANCES_EXPORT_PERIOD_SECS)).thenReturn(900);
        lenient()
                .when(properties.getBooleanProperty(BALANCES_EXPORT_TOKEN_BALANCES))
                .thenReturn(true);
        lenient()
                .when(properties.getLongProperty(BALANCES_NODE_BALANCE_WARN_THRESHOLD))
                .thenReturn(0L);
        lenient()
                .when(properties.getBooleanProperty(BALANCES_COMPRESS_ON_CREATION))
                .thenReturn(false);
        lenient().when(properties.getIntProperty(CACHE_RECORDS_TTL)).thenReturn(180);
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS))
                .thenReturn(false);
        lenient().when(properties.getBooleanProperty(CONTRACTS_ALLOW_CREATE2)).thenReturn(true);
        lenient().when(properties.getIntProperty(CONTRACTS_CHAIN_ID)).thenReturn(295);
        lenient().when(properties.getLongProperty(CONTRACTS_DEFAULT_LIFETIME)).thenReturn(7890000L);
        lenient().when(properties.getStringProperty(CONTRACTS_EVM_VERSION)).thenReturn("v0.30");
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_DYNAMIC_EVM_VERSION))
                .thenReturn(false);
        lenient()
                .when(properties.getIntProperty(CONTRACTS_FREE_STORAGE_TIER_LIMIT))
                .thenReturn(100);
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_ITEMIZE_STORAGE_FEES))
                .thenReturn(true);
        // NU:
        // lenient().when(properties.getIntProperty(CONTRACTS_KNOWN_BLOCK_HASH|||com.hedera.services.context.properties.PropertySource$$Lambda$102/0x000000080018b1a0@53b7f657>>>)).thenReturn(<nothing>);
        lenient()
                .when(properties.getIntProperty(CONTRACTS_LOCAL_CALL_EST_RET_BYTES))
                .thenReturn(32);
        lenient().when(properties.getLongProperty(CONTRACTS_MAX_GAS_PER_SEC)).thenReturn(15000000L);
        lenient()
                .when(properties.getLongProperty(CONTRACTS_MAX_KV_PAIRS_AGGREGATE))
                .thenReturn(500_000_000L);
        lenient()
                .when(properties.getIntProperty(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL))
                .thenReturn(163840);
        lenient().when(properties.getLongProperty(CONTRACTS_MAX_NUM)).thenReturn(5_000_000L);
        lenient()
                .when(properties.getIntProperty(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT))
                .thenReturn(20);
        lenient()
                .when(properties.getLongProperty(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST))
                .thenReturn(100L);
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS))
                .thenReturn(true);
        lenient()
                .when(properties.getLongProperty(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST))
                .thenReturn(10000L);
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE))
                .thenReturn(true);
        lenient()
                .when(properties.getAccessListProperty(EXPIRY_MIN_CYCLE_ENTRY_CAPACITY))
                .thenReturn(
                        List.of(
                                MapAccessType.ACCOUNTS_GET,
                                MapAccessType.ACCOUNTS_GET_FOR_MODIFY,
                                MapAccessType.STORAGE_GET,
                                MapAccessType.STORAGE_REMOVE,
                                MapAccessType.STORAGE_PUT));
        lenient()
                .when(properties.getStringProperty(EXPIRY_THROTTLE_RESOURCE))
                .thenReturn("expiry-throttle.json");
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_REDIRECT_TOKEN_CALLS))
                .thenReturn(true);
        lenient()
                .when(properties.getLongProperty(CONTRACTS_REFERENCE_SLOT_LIFETIME))
                .thenReturn(31536000L);
        lenient()
                .when(properties.getLongProperty(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT))
                .thenReturn(5000000L);
        // NU:
        // lenient().when(properties.getIntProperty(CONTRACTS_SIDECARS|||com.hedera.services.context.properties.PropertySource$$Lambda$107/0x0000000800190910@5a37d3ed>>>)).thenReturn(<nothing>);
        lenient()
                .when(properties.getStringProperty(CONTRACTS_STORAGE_SLOT_PRICE_TIERS))
                .thenReturn("0til100M,2000til450M");
        lenient()
                .when(properties.getBooleanProperty(CONTRACTS_THROTTLE_THROTTLE_BY_GAS))
                .thenReturn(true);
        lenient().when(properties.getIntProperty(FEES_MIN_CONGESTION_PERIOD)).thenReturn(60);
        lenient()
                .when(
                        properties.getCongestionMultiplierProperty(
                                FEES_PERCENT_CONGESTION_MULTIPLIERS))
                .thenReturn(CongestionMultipliers.from("90,10x,95,25x,99,100x"));
        lenient()
                .when(properties.getIntProperty(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER))
                .thenReturn(380);
        lenient().when(properties.getLongProperty(FILES_MAX_NUM)).thenReturn(1_000_000L);
        lenient().when(properties.getIntProperty(FILES_MAX_SIZE_KB)).thenReturn(1024);
        lenient()
                .when(properties.getIntProperty(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB))
                .thenReturn(256);
        lenient().when(properties.getIntProperty(HEDERA_TXN_MAX_MEMO_UTF8_BYTES)).thenReturn(100);
        lenient().when(properties.getLongProperty(HEDERA_TXN_MAX_VALID_DURATION)).thenReturn(180L);
        lenient().when(properties.getLongProperty(HEDERA_TXN_MIN_VALID_DURATION)).thenReturn(15L);
        lenient()
                .when(properties.getIntProperty(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS))
                .thenReturn(10);
        lenient().when(properties.getIntProperty(HEDERA_ALLOWANCES_MAX_TXN_LIMIT)).thenReturn(20);
        lenient()
                .when(properties.getIntProperty(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT))
                .thenReturn(100);
        lenient()
                .when(properties.getBooleanProperty(HEDERA_ALLOWANCES_IS_ENABLED))
                .thenReturn(true);
        lenient()
                .when(properties.getTypesProperty(AUTO_RENEW_TARGET_TYPES))
                .thenReturn(Set.of(CONTRACT));
        lenient()
                .when(properties.getIntProperty(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN))
                .thenReturn(100);
        lenient()
                .when(properties.getIntProperty(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE))
                .thenReturn(2);
        lenient().when(properties.getLongProperty(AUTO_RENEW_GRACE_PERIOD)).thenReturn(604800L);
        lenient()
                .when(properties.getIntProperty(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED))
                .thenReturn(1024);
        lenient()
                .when(properties.getLongProperty(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS))
                .thenReturn(3L);
        lenient()
                .when(properties.getLongProperty(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS))
                .thenReturn(50L);
        lenient()
                .when(properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION))
                .thenReturn(8000001L);
        lenient()
                .when(properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION))
                .thenReturn(2592000L);
        lenient().when(properties.getIntProperty(LEDGER_CHANGE_HIST_MEM_SECS)).thenReturn(20);
        lenient().when(properties.getIntProperty(LEDGER_XFER_BAL_CHANGES_MAX_LEN)).thenReturn(20);
        lenient().when(properties.getLongProperty(LEDGER_FUNDING_ACCOUNT)).thenReturn(98L);
        lenient().when(properties.getIntProperty(LEDGER_TRANSFERS_MAX_LEN)).thenReturn(10);
        lenient().when(properties.getIntProperty(LEDGER_TOKEN_TRANSFERS_MAX_LEN)).thenReturn(10);
        lenient().when(properties.getIntProperty(LEDGER_NFT_TRANSFERS_MAX_LEN)).thenReturn(10);
        lenient()
                .when(properties.getIntProperty(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT))
                .thenReturn(180);
        lenient()
                .when(properties.getIntProperty(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS))
                .thenReturn(1800);
        lenient()
                .when(properties.getIntProperty(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT))
                .thenReturn(25);
        lenient().when(properties.getLongProperty(RATES_MIDNIGHT_CHECK_INTERVAL)).thenReturn(1L);
        lenient()
                .when(properties.getFunctionsProperty(SCHEDULING_WHITE_LIST))
                .thenReturn(
                        Set.of(
                                HederaFunctionality.ConsensusSubmitMessage,
                                HederaFunctionality.CryptoTransfer,
                                HederaFunctionality.TokenMint,
                                HederaFunctionality.TokenBurn,
                                HederaFunctionality.CryptoApproveAllowance));
        lenient()
                .when(properties.getBooleanProperty(SCHEDULING_LONG_TERM_ENABLED))
                .thenReturn(false);
        lenient().when(properties.getLongProperty(SCHEDULING_MAX_NUM)).thenReturn(10_000_000L);
        lenient().when(properties.getLongProperty(SCHEDULING_MAX_TXN_PER_SEC)).thenReturn(100L);
        lenient()
                .when(properties.getLongProperty(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS))
                .thenReturn(5356800L);
        lenient()
                .when(properties.getBooleanProperty(SIGS_EXPAND_FROM_IMMUTABLE_STATE))
                .thenReturn(true);
        lenient().when(properties.getIntProperty(STAKING_FEES_NODE_REWARD_PERCENT)).thenReturn(0);
        lenient()
                .when(properties.getIntProperty(STAKING_FEES_STAKING_REWARD_PERCENT))
                .thenReturn(100);
        lenient()
                .when(properties.getLongProperty(STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR))
                .thenReturn(17_808L);
        // NU:
        // lenient().when(properties.getIntProperty(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS|||com.hedera.services.context.properties.PropertySource$$Lambda$99/0x000000080018a6c0@6b58b9e9>>>)).thenReturn(<nothing>);
        lenient().when(properties.getBooleanProperty(STAKING_IS_ENABLED)).thenReturn(true);
        lenient().when(properties.getLongProperty(STAKING_REWARD_RATE)).thenReturn(0L);
        lenient()
                .when(properties.getBooleanProperty(STAKING_REQUIRE_MIN_STAKE_TO_REWARD))
                .thenReturn(false);
        lenient()
                .when(properties.getLongProperty(STAKING_START_THRESH))
                .thenReturn(250_000_000_00_000_000L);
        lenient()
                .when(properties.getLongProperty(TOKENS_MAX_AGGREGATE_RELS))
                .thenReturn(10_000_000L);
        lenient().when(properties.getLongProperty(TOKENS_MAX_NUM)).thenReturn(1_000_000L);
        lenient().when(properties.getIntProperty(TOKENS_MAX_PER_ACCOUNT)).thenReturn(1000);
        lenient().when(properties.getIntProperty(TOKENS_MAX_RELS_PER_INFO_QUERY)).thenReturn(1000);
        lenient().when(properties.getIntProperty(TOKENS_MAX_SYMBOL_UTF8_BYTES)).thenReturn(100);
        lenient().when(properties.getIntProperty(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES)).thenReturn(100);
        lenient().when(properties.getIntProperty(TOKENS_MAX_CUSTOM_FEES_ALLOWED)).thenReturn(10);
        lenient().when(properties.getIntProperty(TOKENS_MAX_CUSTOM_FEE_DEPTH)).thenReturn(2);
        lenient().when(properties.getBooleanProperty(TOKENS_NFTS_ARE_ENABLED)).thenReturn(true);
        lenient().when(properties.getIntProperty(TOKENS_NFTS_MAX_METADATA_BYTES)).thenReturn(100);
        lenient().when(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_BURN)).thenReturn(10);
        lenient().when(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE)).thenReturn(10);
        lenient().when(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_MINT)).thenReturn(10);
        lenient()
                .when(properties.getLongProperty(TOKENS_NFTS_MAX_ALLOWED_MINTS))
                .thenReturn(5000000L);
        lenient().when(properties.getLongProperty(TOKENS_NFTS_MAX_QUERY_RANGE)).thenReturn(100L);
        lenient()
                .when(properties.getThrottleScaleFactor(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR))
                .thenReturn(ThrottleReqOpsScaleFactor.from("5:2"));
        lenient()
                .when(properties.getBooleanProperty(TOKENS_NFTS_USE_TREASURY_WILD_CARDS))
                .thenReturn(true);
        lenient()
                .when(properties.getBooleanProperty(TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .thenReturn(false);
        lenient().when(properties.getLongProperty(TOPICS_MAX_NUM)).thenReturn(1_000_000L);
        lenient()
                .when(properties.getStringProperty(UPGRADE_ARTIFACTS_PATH))
                .thenReturn("../data/services-hedera/HapiApp2.0/data/upgrade/current");
        lenient()
                .when(properties.getStringProperty(DEV_DEFAULT_LISTENING_NODE_ACCOUNT))
                .thenReturn("0.0.3");
        lenient()
                .when(properties.getBooleanProperty(DEV_ONLY_DEFAULT_NODE_LISTENS))
                .thenReturn(true);
        lenient().when(properties.getIntProperty(GRPC_PORT)).thenReturn(50211);
        lenient().when(properties.getIntProperty(GRPC_TLS_PORT)).thenReturn(50212);
        lenient()
                .when(properties.getStringProperty(HEDERA_ACCOUNTS_EXPORT_PATH))
                .thenReturn("../data/onboard/exportedAccount.txt");
        lenient()
                .when(properties.getBooleanProperty(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP))
                .thenReturn(false);
        lenient()
                .when(properties.getProfileProperty(HEDERA_PROFILES_ACTIVE))
                .thenReturn(Profile.PROD);
        lenient()
                .when(properties.getBooleanProperty(HEDERA_RECORD_STREAM_IS_ENABLED))
                .thenReturn(true);
        lenient()
                .when(properties.getStringProperty(HEDERA_RECORD_STREAM_LOG_DIR))
                .thenReturn("../data/recordStreams");
        lenient()
                .when(properties.getStringProperty(HEDERA_RECORD_STREAM_SIDE_CAR_DIR))
                .thenReturn("sidecar");
        lenient().when(properties.getLongProperty(HEDERA_RECORD_STREAM_LOG_PERIOD)).thenReturn(2L);
        lenient()
                .when(properties.getIntProperty(HEDERA_RECORD_STREAM_QUEUE_CAPACITY))
                .thenReturn(5000);
        lenient()
                .when(properties.getIntProperty(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION))
                .thenReturn(6);
        lenient()
                .when(properties.getIntProperty(HEDERA_RECORD_STREAM_SIG_FILE_VERSION))
                .thenReturn(6);
        lenient()
                .when(properties.getBooleanProperty(HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION))
                .thenReturn(false);
        lenient()
                .when(
                        properties.getBooleanProperty(
                                HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION))
                .thenReturn(false);
        lenient().when(properties.getIntProperty(ISS_RESET_PERIOD)).thenReturn(60);
        lenient().when(properties.getIntProperty(ISS_ROUNDS_TO_LOG)).thenReturn(5000);
        lenient().when(properties.getProfileProperty(NETTY_MODE)).thenReturn(Profile.PROD);
        lenient().when(properties.getIntProperty(NETTY_PROD_FLOW_CONTROL_WINDOW)).thenReturn(10240);
        lenient().when(properties.getIntProperty(NETTY_PROD_MAX_CONCURRENT_CALLS)).thenReturn(10);
        lenient().when(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_AGE)).thenReturn(15L);
        lenient()
                .when(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_AGE_GRACE))
                .thenReturn(5L);
        lenient().when(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_IDLE)).thenReturn(10L);
        lenient().when(properties.getLongProperty(NETTY_PROD_KEEP_ALIVE_TIME)).thenReturn(10L);
        lenient().when(properties.getLongProperty(NETTY_PROD_KEEP_ALIVE_TIMEOUT)).thenReturn(3L);
        lenient().when(properties.getIntProperty(NETTY_START_RETRIES)).thenReturn(90);
        lenient().when(properties.getLongProperty(NETTY_START_RETRY_INTERVAL_MS)).thenReturn(1000L);
        lenient().when(properties.getStringProperty(NETTY_TLS_CERT_PATH)).thenReturn("hedera.crt");
        lenient().when(properties.getStringProperty(NETTY_TLS_KEY_PATH)).thenReturn("hedera.key");
        lenient().when(properties.getIntProperty(QUERIES_BLOB_LOOK_UP_RETRIES)).thenReturn(3);
        lenient()
                .when(properties.getStringsProperty(STATS_CONS_THROTTLES_TO_SAMPLE))
                .thenReturn(List.of("<GAS>", "ThroughputLimits", "CreationLimits"));
        lenient()
                .when(properties.getStringsProperty(STATS_HAPI_THROTTLES_TO_SAMPLE))
                .thenReturn(
                        List.of(
                                "<GAS>",
                                "ThroughputLimits",
                                "OffHeapQueryLimits",
                                "CreationLimits",
                                "FreeQueryLimits"));
        lenient().when(properties.getIntProperty(STATS_EXECUTION_TIMES_TO_TRACK)).thenReturn(0);
        lenient()
                .when(properties.getLongProperty(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS))
                .thenReturn(3000L);
        lenient()
                .when(properties.getLongProperty(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS))
                .thenReturn(3000L);
        lenient()
                .when(properties.getLongProperty(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS))
                .thenReturn(1000L);
        lenient()
                .when(properties.getDoubleProperty(STATS_RUNNING_AVG_HALF_LIFE_SECS))
                .thenReturn(10.0);
        lenient()
                .when(properties.getDoubleProperty(STATS_SPEEDOMETER_HALF_LIFE_SECS))
                .thenReturn(10.0);
        lenient().when(properties.getIntProperty(HEDERA_PREFETCH_QUEUE_CAPACITY)).thenReturn(70000);
        lenient().when(properties.getIntProperty(HEDERA_PREFETCH_THREAD_POOL_SIZE)).thenReturn(4);
        lenient()
                .when(properties.getIntProperty(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS))
                .thenReturn(600);
        lenient().when(properties.getBooleanProperty(UTIL_PRNG_IS_ENABLED)).thenReturn(true);
        lenient()
                .when(properties.getBooleanProperty(TOKENS_AUTO_CREATIONS_ENABLED))
                .thenReturn(true);

        return properties;
    }
}
