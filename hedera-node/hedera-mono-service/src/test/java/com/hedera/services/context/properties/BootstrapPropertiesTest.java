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

import static com.hedera.services.context.properties.PropertyNames.*;
import static com.hedera.services.context.properties.PropertyNames.TOPICS_MAX_NUM;
import static com.hedera.services.contracts.ContractsV_0_30Module.EVM_VERSION_0_30;
import static com.hedera.services.stream.proto.SidecarType.CONTRACT_ACTION;
import static com.hedera.services.stream.proto.SidecarType.CONTRACT_BYTECODE;
import static com.hedera.services.stream.proto.SidecarType.CONTRACT_STATE_CHANGE;
import static com.hedera.services.sysfiles.domain.KnownBlockValues.MISSING_BLOCK_VALUES;
import static com.hedera.services.throttling.MapAccessType.*;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static java.util.Map.entry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({LogCaptureExtension.class})
class BootstrapPropertiesTest {
    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private BootstrapProperties subject = new BootstrapProperties();

    private static final String STD_PROPS_RESOURCE = "bootstrap/standard.properties";
    private static final String INVALID_PROPS_RESOURCE = "bootstrap/not.properties";
    private static final String UNREADABLE_PROPS_RESOURCE = "bootstrap/unreadable.properties";
    private static final String INCOMPLETE_STD_PROPS_RESOURCE = "bootstrap/incomplete.properties";

    private static final String OVERRIDE_PROPS_LOC =
            "src/test/resources/bootstrap/override.properties";
    private static final String EMPTY_OVERRIDE_PROPS_LOC =
            "src/test/resources/bootstrap/empty-override.properties";

    private static final Map<String, Object> expectedProps =
            Map.ofEntries(
                    entry(BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE, "feeSchedules.json"),
                    entry(
                            BOOTSTRAP_GENESIS_PUBLIC_KEY,
                            "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"),
                    entry(BOOTSTRAP_HAPI_PERMISSIONS_PATH, "data/config/api-permission.properties"),
                    entry(BOOTSTRAP_NETWORK_PROPERTIES_PATH, "data/config/application.properties"),
                    entry(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV, 1),
                    entry(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV, 12),
                    entry(BOOTSTRAP_RATES_CURRENT_EXPIRY, 4102444800L),
                    entry(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV, 1),
                    entry(BOOTSTRAP_RATES_NEXT_CENT_EQUIV, 15),
                    entry(BOOTSTRAP_RATES_NEXT_EXPIRY, 4102444800L),
                    entry(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY, 1812637686L),
                    entry(BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE, "throttles.json"),
                    entry(ACCOUNTS_ADDRESS_BOOK_ADMIN, 55L),
                    entry(BALANCES_EXPORT_DIR_PATH, "/opt/hgcapp/accountBalances/"),
                    entry(BALANCES_EXPORT_ENABLED, true),
                    entry(BALANCES_EXPORT_PERIOD_SECS, 900),
                    entry(BALANCES_EXPORT_TOKEN_BALANCES, true),
                    entry(BALANCES_NODE_BALANCE_WARN_THRESHOLD, 0L),
                    entry(BALANCES_COMPRESS_ON_CREATION, true),
                    entry(ACCOUNTS_EXCHANGE_RATES_ADMIN, 57L),
                    entry(ACCOUNTS_FEE_SCHEDULE_ADMIN, 56L),
                    entry(ACCOUNTS_NODE_REWARD_ACCOUNT, 801L),
                    entry(ACCOUNTS_STAKING_REWARD_ACCOUNT, 800L),
                    entry(ACCOUNTS_FREEZE_ADMIN, 58L),
                    entry(ACCOUNTS_LAST_THROTTLE_EXEMPT, 100L),
                    entry(ACCOUNTS_SYSTEM_ADMIN, 50L),
                    entry(ACCOUNTS_SYSTEM_DELETE_ADMIN, 59L),
                    entry(ACCOUNTS_SYSTEM_UNDELETE_ADMIN, 60L),
                    entry(ACCOUNTS_TREASURY, 2L),
                    entry(AUTO_RENEW_GRANT_FREE_RENEWALS, false),
                    entry(CONTRACTS_ALLOW_CREATE2, true),
                    entry(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS, false),
                    entry(CONTRACTS_DEFAULT_LIFETIME, 7890000L),
                    entry(CONTRACTS_DYNAMIC_EVM_VERSION, false),
                    entry(CONTRACTS_EVM_VERSION, EVM_VERSION_0_30),
                    entry(CONTRACTS_LOCAL_CALL_EST_RET_BYTES, 32),
                    entry(CONTRACTS_MAX_GAS_PER_SEC, 15000000L),
                    entry(CONTRACTS_MAX_KV_PAIRS_AGGREGATE, 500_000_000L),
                    entry(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL, 163_840),
                    entry(CONTRACTS_CHAIN_ID, 295),
                    entry(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, true),
                    entry(CONTRACTS_KNOWN_BLOCK_HASH, MISSING_BLOCK_VALUES),
                    entry(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, 20),
                    entry(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT, 5000000L),
                    entry(CONTRACTS_REDIRECT_TOKEN_CALLS, true),
                    entry(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST, 100L),
                    entry(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST, 10000L),
                    entry(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS, true),
                    entry(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE, true),
                    entry(DEV_ONLY_DEFAULT_NODE_LISTENS, true),
                    entry(DEV_DEFAULT_LISTENING_NODE_ACCOUNT, "0.0.3"),
                    entry(ENTITIES_MAX_LIFETIME, 3153600000L),
                    entry(ENTITIES_SYSTEM_DELETABLE, EnumSet.of(EntityType.FILE)),
                    entry(EXPIRY_THROTTLE_RESOURCE, "expiry-throttle.json"),
                    entry(
                            EXPIRY_MIN_CYCLE_ENTRY_CAPACITY,
                            List.of(
                                    ACCOUNTS_GET,
                                    ACCOUNTS_GET_FOR_MODIFY,
                                    STORAGE_GET,
                                    STORAGE_GET,
                                    STORAGE_REMOVE,
                                    STORAGE_PUT)),
                    entry(
                            FEES_PERCENT_CONGESTION_MULTIPLIERS,
                            CongestionMultipliers.from("90,10x,95,25x,99,100x")),
                    entry(FEES_MIN_CONGESTION_PERIOD, 60),
                    entry(FILES_ADDRESS_BOOK, 101L),
                    entry(FILES_NETWORK_PROPERTIES, 121L),
                    entry(FILES_EXCHANGE_RATES, 112L),
                    entry(FILES_FEE_SCHEDULES, 111L),
                    entry(FILES_HAPI_PERMISSIONS, 122L),
                    entry(FILES_THROTTLE_DEFINITIONS, 123L),
                    entry(FILES_NODE_DETAILS, 102L),
                    entry(FILES_SOFTWARE_UPDATE_RANGE, Pair.of(150L, 159L)),
                    entry(GRPC_PORT, 50211),
                    entry(GRPC_TLS_PORT, 50212),
                    entry(HEDERA_ACCOUNTS_EXPORT_PATH, "data/onboard/exportedAccount.txt"),
                    entry(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP, false),
                    entry(HEDERA_FIRST_USER_ENTITY, 1001L),
                    entry(HEDERA_PREFETCH_QUEUE_CAPACITY, 10000),
                    entry(HEDERA_PREFETCH_THREAD_POOL_SIZE, 2),
                    entry(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS, 120),
                    entry(HEDERA_PROFILES_ACTIVE, Profile.PROD),
                    entry(HEDERA_REALM, 0L),
                    entry(HEDERA_RECORD_STREAM_LOG_DIR, "/opt/hgcapp/recordStreams"),
                    entry(HEDERA_RECORD_STREAM_SIDE_CAR_DIR, "sidecar"),
                    entry(HEDERA_RECORD_STREAM_LOG_PERIOD, 2L),
                    entry(HEDERA_RECORD_STREAM_IS_ENABLED, true),
                    entry(HEDERA_RECORD_STREAM_QUEUE_CAPACITY, 5000),
                    entry(HEDERA_SHARD, 0L),
                    entry(HEDERA_TXN_MAX_MEMO_UTF8_BYTES, 100),
                    entry(HEDERA_TXN_MIN_VALID_DURATION, 15L),
                    entry(HEDERA_TXN_MAX_VALID_DURATION, 180L),
                    entry(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS, 10),
                    entry(LEDGER_ID, "0x03"),
                    entry(LEDGER_CHANGE_HIST_MEM_SECS, 20),
                    entry(LEDGER_FUNDING_ACCOUNT, 98L),
                    entry(LEDGER_NUM_SYSTEM_ACCOUNTS, 100),
                    entry(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT, 180),
                    entry(LEDGER_TRANSFERS_MAX_LEN, 10),
                    entry(LEDGER_TOKEN_TRANSFERS_MAX_LEN, 10),
                    entry(LEDGER_TOTAL_TINY_BAR_FLOAT, 5000000000000000000L),
                    entry(AUTO_CREATION_ENABLED, true),
                    entry(AUTO_RENEW_TARGET_TYPES, EnumSet.of(EntityType.CONTRACT)),
                    entry(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN, 100),
                    entry(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE, 2),
                    entry(AUTO_RENEW_GRACE_PERIOD, 604800L),
                    entry(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, 8000001L),
                    entry(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, 2592000L),
                    entry(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, 1800),
                    entry(ISS_RESET_PERIOD, 60),
                    entry(ISS_ROUNDS_TO_LOG, 5000),
                    entry(NETTY_MODE, Profile.PROD),
                    entry(NETTY_PROD_FLOW_CONTROL_WINDOW, 10240),
                    entry(NETTY_PROD_MAX_CONCURRENT_CALLS, 10),
                    entry(NETTY_PROD_MAX_CONNECTION_AGE, 15L),
                    entry(NETTY_PROD_MAX_CONNECTION_AGE_GRACE, 5L),
                    entry(NETTY_PROD_MAX_CONNECTION_IDLE, 10L),
                    entry(NETTY_PROD_KEEP_ALIVE_TIME, 10L),
                    entry(NETTY_PROD_KEEP_ALIVE_TIMEOUT, 3L),
                    entry(NETTY_START_RETRIES, 90),
                    entry(NETTY_START_RETRY_INTERVAL_MS, 1_000L),
                    entry(NETTY_TLS_CERT_PATH, "hedera.crt"),
                    entry(NETTY_TLS_KEY_PATH, "hedera.key"),
                    entry(QUERIES_BLOB_LOOK_UP_RETRIES, 3),
                    entry(TOKENS_MAX_RELS_PER_INFO_QUERY, 1_000),
                    entry(TOKENS_MAX_PER_ACCOUNT, 1_000),
                    entry(TOKENS_MAX_SYMBOL_UTF8_BYTES, 100),
                    entry(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES, 100),
                    entry(TOKENS_MAX_CUSTOM_FEES_ALLOWED, 10),
                    entry(TOKENS_MAX_CUSTOM_FEE_DEPTH, 2),
                    entry(FILES_MAX_SIZE_KB, 1024),
                    entry(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER, 380),
                    entry(CACHE_RECORDS_TTL, 180),
                    entry(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT, 25),
                    entry(RATES_MIDNIGHT_CHECK_INTERVAL, 1L),
                    entry(SCHEDULING_LONG_TERM_ENABLED, true),
                    entry(SCHEDULING_MAX_TXN_PER_SEC, 100L),
                    entry(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS, 5356800L),
                    entry(
                            SCHEDULING_WHITE_LIST,
                            Set.of(CryptoTransfer, TokenMint, TokenBurn, ConsensusSubmitMessage)),
                    entry(SIGS_EXPAND_FROM_IMMUTABLE_STATE, true),
                    entry(
                            STATS_CONS_THROTTLES_TO_SAMPLE,
                            List.of("<GAS>", "ThroughputLimits", "CreationLimits")),
                    entry(
                            STATS_HAPI_THROTTLES_TO_SAMPLE,
                            List.of(
                                    "<GAS>",
                                    "ThroughputLimits",
                                    "OffHeapQueryLimits",
                                    "CreationLimits",
                                    "FreeQueryLimits")),
                    entry(STATS_RUNNING_AVG_HALF_LIFE_SECS, 10.0),
                    entry(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS, 3_000L),
                    entry(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS, 3_000L),
                    entry(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS, 1_000L),
                    entry(STATS_SPEEDOMETER_HALF_LIFE_SECS, 10.0),
                    entry(STATS_EXECUTION_TIMES_TO_TRACK, 0),
                    entry(STAKING_IS_ENABLED, true),
                    entry(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS, Map.of()),
                    entry(STAKING_PERIOD_MINS, 1440L),
                    entry(STAKING_REQUIRE_MIN_STAKE_TO_REWARD, false),
                    entry(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS, 365),
                    entry(STAKING_REWARD_RATE, 0L),
                    entry(STAKING_START_THRESH, 25000000000000000L),
                    entry(STAKING_FEES_NODE_REWARD_PERCENT, 0),
                    entry(STAKING_FEES_STAKING_REWARD_PERCENT, 100),
                    entry(STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR, 17808L),
                    entry(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED, 1024),
                    entry(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS, 3L),
                    entry(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS, 50L),
                    entry(LEDGER_NFT_TRANSFERS_MAX_LEN, 10),
                    entry(LEDGER_XFER_BAL_CHANGES_MAX_LEN, 20),
                    entry(TOKENS_NFTS_ARE_ENABLED, true),
                    entry(TOKENS_NFTS_USE_TREASURY_WILD_CARDS, true),
                    entry(TOKENS_NFTS_MAX_QUERY_RANGE, 100L),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE, 10),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_MINT, 10),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_BURN, 10),
                    entry(TOKENS_NFTS_MAX_METADATA_BYTES, 100),
                    entry(TOKENS_NFTS_MAX_ALLOWED_MINTS, 5000000L),
                    entry(
                            TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR,
                            ThrottleReqOpsScaleFactor.from("5:2")),
                    entry(TOKENS_NFTS_USE_VIRTUAL_MERKLE, false),
                    entry(
                            UPGRADE_ARTIFACTS_PATH,
                            "/opt/hgcapp/services-hedera/HapiApp2.0/data/upgrade/current"),
                    entry(HEDERA_ALLOWANCES_MAX_TXN_LIMIT, 20),
                    entry(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, 100),
                    entry(HEDERA_ALLOWANCES_IS_ENABLED, true),
                    entry(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS, false),
                    entry(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION, 6),
                    entry(HEDERA_RECORD_STREAM_SIG_FILE_VERSION, 6),
                    entry(ACCOUNTS_MAX_NUM, 5_000_000L),
                    entry(CONTRACTS_MAX_NUM, 5_000_000L),
                    entry(CONTRACTS_STORAGE_SLOT_PRICE_TIERS, "0til100M,2000til450M"),
                    entry(CONTRACTS_REFERENCE_SLOT_LIFETIME, 31536000L),
                    entry(CONTRACTS_ITEMIZE_STORAGE_FEES, true),
                    entry(CONTRACTS_FREE_STORAGE_TIER_LIMIT, 100),
                    entry(FILES_MAX_NUM, 1_000_000L),
                    entry(SCHEDULING_MAX_NUM, 10_000_000L),
                    entry(TOKENS_MAX_NUM, 1_000_000L),
                    entry(TOPICS_MAX_NUM, 1_000_000L),
                    entry(TOKENS_MAX_AGGREGATE_RELS, 10_000_000L),
                    entry(UTIL_PRNG_IS_ENABLED, true),
                    entry(
                            CONTRACTS_SIDECARS,
                            EnumSet.of(
                                    SidecarType.CONTRACT_STATE_CHANGE,
                                    SidecarType.CONTRACT_BYTECODE)),
                    entry(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB, 256),
                    entry(HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION, true),
                    entry(HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION, false),
                    entry(HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION, true),
                    entry(TOKENS_AUTO_CREATIONS_ENABLED, true));

    @Test
    void containsProperty() {
        assertTrue(subject.containsProperty(TOKENS_NFTS_MAX_QUERY_RANGE));
    }

    @BeforeEach
    void setUp() {
        subject.bootstrapOverridePropsLoc = EMPTY_OVERRIDE_PROPS_LOC;
    }

    @Test
    void throwsIseIfUnreadable() {
        subject.bootstrapPropsResource = UNREADABLE_PROPS_RESOURCE;

        final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
        final var msg =
                String.format("'%s' contains unrecognized properties:", UNREADABLE_PROPS_RESOURCE);
        assertTrue(ise.getMessage().startsWith(msg));
    }

    @Test
    void throwsIseIfIoExceptionOccurs() {
        final var bkup = BootstrapProperties.resourceStreamProvider;
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
        BootstrapProperties.resourceStreamProvider =
                ignore -> {
                    throw new IOException("Oops!");
                };

        final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
        final var msg = String.format("'%s' could not be loaded!", STD_PROPS_RESOURCE);
        assertEquals(msg, ise.getMessage());

        BootstrapProperties.resourceStreamProvider = bkup;
    }

    @Test
    void throwsIseIfInvalid() {
        subject.bootstrapPropsResource = INVALID_PROPS_RESOURCE;

        final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
        final var msg =
                String.format("'%s' contains unrecognized properties:", INVALID_PROPS_RESOURCE);
        assertTrue(ise.getMessage().startsWith(msg));
    }

    @Test
    void ensuresFilePropsFromExtant() {
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;

        subject.ensureProps();

        for (String name : BootstrapProperties.BOOTSTRAP_PROP_NAMES) {
            assertEquals(
                    expectedProps.get(name),
                    subject.getProperty(name),
                    name + " has the wrong value!");
        }
        for (final var key : expectedProps.keySet()) {
            if (!BootstrapProperties.BOOTSTRAP_PROP_NAMES.contains(key)) {
                System.out.println(key);
            }
        }
        assertEquals(expectedProps, subject.bootstrapProps);
    }

    @Test
    void includesOverrides() {
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
        subject.bootstrapOverridePropsLoc = OVERRIDE_PROPS_LOC;

        subject.ensureProps();

        assertEquals(30, subject.getProperty(TOKENS_MAX_RELS_PER_INFO_QUERY));
        assertEquals(30, subject.getProperty(TOKENS_MAX_PER_ACCOUNT));
        assertEquals(
                EnumSet.of(CONTRACT_STATE_CHANGE, CONTRACT_ACTION, CONTRACT_BYTECODE),
                subject.getProperty(CONTRACTS_SIDECARS));
    }

    @Test
    void doesntThrowOnMissingOverridesFile() {
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
        subject.bootstrapOverridePropsLoc = "im-not-here";

        assertDoesNotThrow(subject::ensureProps);
    }

    @Test
    void throwsIaeOnMissingPropRequest() {
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;

        subject.ensureProps();

        final var ise =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.getProperty("not-a-real-prop"));
        assertEquals("Argument 'name=not-a-real-prop' is invalid!", ise.getMessage());
    }

    @Test
    void throwsIseIfMissingProps() {
        subject.bootstrapPropsResource = INCOMPLETE_STD_PROPS_RESOURCE;

        final var ise = assertThrows(IllegalStateException.class, subject::ensureProps);
        final var msg = String.format("'%s' is missing properties:", INCOMPLETE_STD_PROPS_RESOURCE);
        assertTrue(ise.getMessage().startsWith(msg));
    }

    @Test
    void logsLoadedPropsOnInit() {
        subject.bootstrapPropsResource = STD_PROPS_RESOURCE;
        subject.getProperty(BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE);

        assertThat(
                logCaptor.infoLogs(),
                contains(Matchers.startsWith(("Resolved bootstrap properties:"))));
    }
}
