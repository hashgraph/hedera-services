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
package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.service.mono.context.properties.PropUtils.loadOverride;
import static java.util.Collections.unmodifiableSet;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class BootstrapProperties implements PropertySource {
    private static final Map<String, Object> MISSING_PROPS = null;

    private static final Function<String, InputStream> nullableResourceStreamProvider =
            BootstrapProperties.class.getClassLoader()::getResourceAsStream;

    private static final Logger log = LogManager.getLogger(BootstrapProperties.class);

    static ThrowingStreamProvider resourceStreamProvider =
            resource -> {
                var in = nullableResourceStreamProvider.apply(resource);
                if (in == null) {
                    throw new IOException(
                            String.format("Resource '%s' cannot be loaded.", resource));
                }
                return in;
            };
    private static ThrowingStreamProvider fileStreamProvider =
            loc -> Files.newInputStream(Paths.get(loc));

    @Inject
    public BootstrapProperties() {
        /* No-op */
    }

    String bootstrapPropsResource = "bootstrap.properties";
    String bootstrapOverridePropsLoc = "data/config/bootstrap.properties";

    Map<String, Object> bootstrapProps = MISSING_PROPS;

    private void initPropsFromResource() throws IllegalStateException {
        final var resourceProps = new Properties();
        load(bootstrapPropsResource, resourceProps);
        loadOverride(bootstrapOverridePropsLoc, resourceProps, fileStreamProvider, log);
        checkForUnrecognizedProps(resourceProps);
        checkForMissingProps(resourceProps);
        resolveBootstrapProps(resourceProps);
    }

    private void checkForUnrecognizedProps(final Properties resourceProps)
            throws IllegalStateException {
        final Set<String> unrecognizedProps = new HashSet<>(resourceProps.stringPropertyNames());
        unrecognizedProps.removeAll(BOOTSTRAP_PROP_NAMES);
        if (!unrecognizedProps.isEmpty()) {
            final var msg =
                    String.format(
                            "'%s' contains unrecognized properties: %s!",
                            bootstrapPropsResource, unrecognizedProps);
            throw new IllegalStateException(msg);
        }
    }

    private void checkForMissingProps(final Properties resourceProps) throws IllegalStateException {
        final var missingProps =
                BOOTSTRAP_PROP_NAMES.stream()
                        .filter(name -> !resourceProps.containsKey(name))
                        .sorted()
                        .toList();
        if (!missingProps.isEmpty()) {
            final var msg =
                    String.format(
                            "'%s' is missing properties: %s!",
                            bootstrapPropsResource, missingProps);
            throw new IllegalStateException(msg);
        }
    }

    private void resolveBootstrapProps(final Properties resourceProps) {
        bootstrapProps = new HashMap<>();
        BOOTSTRAP_PROP_NAMES.forEach(
                prop ->
                        bootstrapProps.put(
                                prop, transformFor(prop).apply(resourceProps.getProperty(prop))));

        final var msg =
                "Resolved bootstrap properties:\n  "
                        + BOOTSTRAP_PROP_NAMES.stream()
                                .sorted()
                                .map(name -> String.format("%s=%s", name, bootstrapProps.get(name)))
                                .collect(Collectors.joining("\n  "));
        log.info(msg);
    }

    private void load(final String resource, final Properties intoProps)
            throws IllegalStateException {
        try (final var fin = resourceStreamProvider.newInputStream(resource)) {
            intoProps.load(fin);
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("'%s' could not be loaded!", resource), e);
        }
    }

    public void ensureProps() throws IllegalStateException {
        if (bootstrapProps == MISSING_PROPS) {
            initPropsFromResource();
        }
    }

    @Override
    public boolean containsProperty(final String name) {
        return BOOTSTRAP_PROP_NAMES.contains(name);
    }

    @Override
    public Object getProperty(final String name) {
        ensureProps();
        if (bootstrapProps.containsKey(name)) {
            return bootstrapProps.get(name);
        } else {
            throw new IllegalArgumentException(
                    String.format("Argument 'name=%s' is invalid!", name));
        }
    }

    @Override
    public Set<String> allPropertyNames() {
        return BOOTSTRAP_PROP_NAMES;
    }

    private static final Set<String> BOOTSTRAP_PROPS =
            Set.of(
                    PropertyNames.BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE,
                    PropertyNames.BOOTSTRAP_GENESIS_PUBLIC_KEY,
                    PropertyNames.BOOTSTRAP_HAPI_PERMISSIONS_PATH,
                    PropertyNames.BOOTSTRAP_NETWORK_PROPERTIES_PATH,
                    PropertyNames.BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV,
                    PropertyNames.BOOTSTRAP_RATES_CURRENT_CENT_EQUIV,
                    PropertyNames.BOOTSTRAP_RATES_CURRENT_EXPIRY,
                    PropertyNames.BOOTSTRAP_RATES_NEXT_HBAR_EQUIV,
                    PropertyNames.BOOTSTRAP_RATES_NEXT_CENT_EQUIV,
                    PropertyNames.BOOTSTRAP_RATES_NEXT_EXPIRY,
                    PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY,
                    PropertyNames.BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE);

    private static final Set<String> GLOBAL_STATIC_PROPS =
            Set.of(
                    PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN,
                    PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN,
                    PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN,
                    PropertyNames.ACCOUNTS_FREEZE_ADMIN,
                    PropertyNames.ACCOUNTS_LAST_THROTTLE_EXEMPT,
                    PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT,
                    PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT,
                    PropertyNames.ACCOUNTS_SYSTEM_ADMIN,
                    PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN,
                    PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN,
                    PropertyNames.ACCOUNTS_TREASURY,
                    PropertyNames.ACCOUNTS_STORE_ON_DISK,
                    PropertyNames.AUTO_RENEW_GRANT_FREE_RENEWALS,
                    PropertyNames.ENTITIES_MAX_LIFETIME,
                    PropertyNames.ENTITIES_SYSTEM_DELETABLE,
                    PropertyNames.FILES_ADDRESS_BOOK,
                    PropertyNames.FILES_NETWORK_PROPERTIES,
                    PropertyNames.FILES_EXCHANGE_RATES,
                    PropertyNames.FILES_FEE_SCHEDULES,
                    PropertyNames.FILES_HAPI_PERMISSIONS,
                    PropertyNames.FILES_NODE_DETAILS,
                    PropertyNames.FILES_SOFTWARE_UPDATE_RANGE,
                    PropertyNames.FILES_THROTTLE_DEFINITIONS,
                    PropertyNames.HEDERA_FIRST_USER_ENTITY,
                    PropertyNames.HEDERA_REALM,
                    PropertyNames.HEDERA_SHARD,
                    PropertyNames.LEDGER_NUM_SYSTEM_ACCOUNTS,
                    PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT,
                    PropertyNames.LEDGER_ID,
                    PropertyNames.STAKING_PERIOD_MINS,
                    PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS);

    static final Set<String> GLOBAL_DYNAMIC_PROPS =
            Set.of(
                    PropertyNames.ACCOUNTS_MAX_NUM,
                    PropertyNames.AUTO_CREATION_ENABLED,
                    PropertyNames.LAZY_CREATION_ENABLED,
                    PropertyNames.CRYPTO_CREATE_WITH_ALIAS_ENABLED,
                    PropertyNames.BALANCES_EXPORT_DIR_PATH,
                    PropertyNames.BALANCES_EXPORT_ENABLED,
                    PropertyNames.BALANCES_EXPORT_PERIOD_SECS,
                    PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES,
                    PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD,
                    PropertyNames.BALANCES_COMPRESS_ON_CREATION,
                    PropertyNames.CACHE_RECORDS_TTL,
                    PropertyNames.CONTRACTS_DEFAULT_LIFETIME,
                    PropertyNames.CONTRACTS_ENFORCE_CREATION_THROTTLE,
                    PropertyNames.CONTRACTS_KNOWN_BLOCK_HASH,
                    PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES,
                    PropertyNames.CONTRACTS_ALLOW_CREATE2,
                    PropertyNames.CONTRACTS_ALLOW_AUTO_ASSOCIATIONS,
                    PropertyNames.CONTRACTS_MAX_GAS_PER_SEC,
                    PropertyNames.CONTRACTS_MAX_KV_PAIRS_AGGREGATE,
                    PropertyNames.CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL,
                    PropertyNames.CONTRACTS_MAX_NUM,
                    PropertyNames.CONTRACTS_CHAIN_ID,
                    PropertyNames.CONTRACTS_SIDECARS,
                    PropertyNames.CONTRACTS_STORAGE_SLOT_PRICE_TIERS,
                    PropertyNames.CONTRACTS_REFERENCE_SLOT_LIFETIME,
                    PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES,
                    PropertyNames.CONTRACTS_FREE_STORAGE_TIER_LIMIT,
                    PropertyNames.CONTRACTS_THROTTLE_THROTTLE_BY_GAS,
                    PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT,
                    PropertyNames.CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT,
                    PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS,
                    PropertyNames.CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST,
                    PropertyNames.CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST,
                    PropertyNames.CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS,
                    PropertyNames.CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE,
                    PropertyNames.CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED,
                    PropertyNames.CONTRACTS_EVM_VERSION,
                    PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION,
                    PropertyNames.EXPIRY_MIN_CYCLE_ENTRY_CAPACITY,
                    PropertyNames.EXPIRY_THROTTLE_RESOURCE,
                    PropertyNames.FILES_MAX_NUM,
                    PropertyNames.FILES_MAX_SIZE_KB,
                    PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB,
                    PropertyNames.FEES_MIN_CONGESTION_PERIOD,
                    PropertyNames.FEES_PERCENT_CONGESTION_MULTIPLIERS,
                    PropertyNames.FEES_PERCENT_UTILIZATION_SCALE_FACTORS,
                    PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER,
                    PropertyNames.HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION,
                    PropertyNames.TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO,
                    PropertyNames.TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC,
                    PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES,
                    PropertyNames.HEDERA_TXN_MAX_VALID_DURATION,
                    PropertyNames.HEDERA_TXN_MIN_VALID_DURATION,
                    PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS,
                    PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION,
                    PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION,
                    PropertyNames.HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION,
                    PropertyNames.HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION,
                    PropertyNames.AUTO_RENEW_TARGET_TYPES,
                    PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN,
                    PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE,
                    PropertyNames.AUTO_RENEW_GRACE_PERIOD,
                    PropertyNames.LEDGER_CHANGE_HIST_MEM_SECS,
                    PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION,
                    PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                    PropertyNames.LEDGER_XFER_BAL_CHANGES_MAX_LEN,
                    PropertyNames.LEDGER_FUNDING_ACCOUNT,
                    PropertyNames.LEDGER_TRANSFERS_MAX_LEN,
                    PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN,
                    PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN,
                    PropertyNames.LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT,
                    PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS,
                    PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT,
                    PropertyNames.RATES_MIDNIGHT_CHECK_INTERVAL,
                    PropertyNames.SCHEDULING_LONG_TERM_ENABLED,
                    PropertyNames.SCHEDULING_MAX_TXN_PER_SEC,
                    PropertyNames.SCHEDULING_MAX_NUM,
                    PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS,
                    PropertyNames.SCHEDULING_WHITE_LIST,
                    PropertyNames.SIGS_EXPAND_FROM_IMMUTABLE_STATE,
                    PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT,
                    PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT,
                    PropertyNames.STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS,
                    PropertyNames.STAKING_IS_ENABLED,
                    PropertyNames.STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR,
                    PropertyNames.STAKING_REQUIRE_MIN_STAKE_TO_REWARD,
                    PropertyNames.STAKING_REWARD_RATE,
                    PropertyNames.STAKING_START_THRESH,
                    PropertyNames.TOKENS_MAX_AGGREGATE_RELS,
                    PropertyNames.TOKENS_STORE_RELS_ON_DISK,
                    PropertyNames.TOKENS_MAX_NUM,
                    PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY,
                    PropertyNames.TOKENS_MAX_PER_ACCOUNT,
                    PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES,
                    PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES,
                    PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED,
                    PropertyNames.TOKENS_MAX_CUSTOM_FEE_DEPTH,
                    PropertyNames.TOKENS_NFTS_ARE_ENABLED,
                    PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES,
                    PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN,
                    PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE,
                    PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT,
                    PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS,
                    PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE,
                    PropertyNames.TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR,
                    PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE,
                    PropertyNames.TOPICS_MAX_NUM,
                    PropertyNames.TOKENS_NFTS_USE_TREASURY_WILD_CARDS,
                    PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED,
                    PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS,
                    PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS,
                    PropertyNames.UPGRADE_ARTIFACTS_PATH,
                    PropertyNames.HEDERA_ALLOWANCES_MAX_TXN_LIMIT,
                    PropertyNames.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT,
                    PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED,
                    PropertyNames.ENTITIES_LIMIT_TOKEN_ASSOCIATIONS,
                    PropertyNames.UTIL_PRNG_IS_ENABLED,
                    PropertyNames.TOKENS_AUTO_CREATIONS_ENABLED);

    static final Set<String> NODE_PROPS =
            Set.of(
                    PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS,
                    PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT,
                    PropertyNames.GRPC_PORT,
                    PropertyNames.GRPC_TLS_PORT,
                    PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH,
                    PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP,
                    PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY,
                    PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE,
                    PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS,
                    PropertyNames.HEDERA_PROFILES_ACTIVE,
                    PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED,
                    PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR,
                    PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR,
                    PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD,
                    PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY,
                    PropertyNames.ISS_RESET_PERIOD,
                    PropertyNames.ISS_ROUNDS_TO_LOG,
                    PropertyNames.NETTY_MODE,
                    PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW,
                    PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS,
                    PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE,
                    PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE,
                    PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE,
                    PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME,
                    PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT,
                    PropertyNames.NETTY_START_RETRIES,
                    PropertyNames.NETTY_START_RETRY_INTERVAL_MS,
                    PropertyNames.NETTY_TLS_CERT_PATH,
                    PropertyNames.NETTY_TLS_KEY_PATH,
                    PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES,
                    PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE,
                    PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE,
                    PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK,
                    PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS,
                    PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS,
                    PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS,
                    PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS,
                    PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS);

    public static final Set<String> BOOTSTRAP_PROP_NAMES =
            unmodifiableSet(
                    Stream.of(
                                    BOOTSTRAP_PROPS,
                                    GLOBAL_STATIC_PROPS,
                                    GLOBAL_DYNAMIC_PROPS,
                                    NODE_PROPS)
                            .flatMap(Set::stream)
                            .collect(toSet()));

    public static Function<String, Object> transformFor(String prop) {
        return PROP_TRANSFORMS.getOrDefault(prop, AS_STRING);
    }

    private static final Map<String, Function<String, Object>> PROP_TRANSFORMS =
            Map.ofEntries(
                    entry(PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_FREEZE_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_LAST_THROTTLE_EXEMPT, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_MAX_NUM, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_SYSTEM_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_TREASURY, AS_LONG),
                    entry(PropertyNames.ACCOUNTS_STORE_ON_DISK, AS_BOOLEAN),
                    entry(PropertyNames.BALANCES_EXPORT_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.BALANCES_EXPORT_PERIOD_SECS, AS_INT),
                    entry(PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD, AS_LONG),
                    entry(PropertyNames.BALANCES_COMPRESS_ON_CREATION, AS_BOOLEAN),
                    entry(PropertyNames.CACHE_RECORDS_TTL, AS_INT),
                    entry(PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS, AS_BOOLEAN),
                    entry(PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES, AS_BOOLEAN),
                    entry(PropertyNames.ENTITIES_MAX_LIFETIME, AS_LONG),
                    entry(PropertyNames.ENTITIES_SYSTEM_DELETABLE, AS_ENTITY_TYPES),
                    entry(PropertyNames.FILES_ADDRESS_BOOK, AS_LONG),
                    entry(PropertyNames.EXPIRY_MIN_CYCLE_ENTRY_CAPACITY, AS_ACCESS_LIST),
                    entry(PropertyNames.FILES_MAX_NUM, AS_LONG),
                    entry(PropertyNames.FILES_MAX_SIZE_KB, AS_INT),
                    entry(PropertyNames.FILES_NETWORK_PROPERTIES, AS_LONG),
                    entry(PropertyNames.FILES_EXCHANGE_RATES, AS_LONG),
                    entry(PropertyNames.FILES_FEE_SCHEDULES, AS_LONG),
                    entry(PropertyNames.FILES_HAPI_PERMISSIONS, AS_LONG),
                    entry(PropertyNames.FILES_NODE_DETAILS, AS_LONG),
                    entry(PropertyNames.FILES_SOFTWARE_UPDATE_RANGE, AS_ENTITY_NUM_RANGE),
                    entry(PropertyNames.FILES_THROTTLE_DEFINITIONS, AS_LONG),
                    entry(PropertyNames.GRPC_PORT, AS_INT),
                    entry(PropertyNames.GRPC_TLS_PORT, AS_INT),
                    entry(PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP, AS_BOOLEAN),
                    entry(PropertyNames.HEDERA_FIRST_USER_ENTITY, AS_LONG),
                    entry(PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY, AS_INT),
                    entry(PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE, AS_INT),
                    entry(PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS, AS_INT),
                    entry(PropertyNames.HEDERA_PROFILES_ACTIVE, AS_PROFILE),
                    entry(PropertyNames.HEDERA_REALM, AS_LONG),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD, AS_LONG),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION, AS_INT),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION, AS_INT),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY, AS_INT),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB, AS_INT),
                    entry(
                            PropertyNames.HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION,
                            AS_BOOLEAN),
                    entry(PropertyNames.TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO, AS_LONG),
                    entry(PropertyNames.TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC, AS_LONG),
                    entry(PropertyNames.HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION, AS_BOOLEAN),
                    entry(
                            PropertyNames.HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION,
                            AS_BOOLEAN),
                    entry(PropertyNames.HEDERA_SHARD, AS_LONG),
                    entry(PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES, AS_INT),
                    entry(PropertyNames.HEDERA_TXN_MAX_VALID_DURATION, AS_LONG),
                    entry(PropertyNames.HEDERA_TXN_MIN_VALID_DURATION, AS_LONG),
                    entry(PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS, AS_INT),
                    entry(PropertyNames.AUTO_CREATION_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.LAZY_CREATION_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.CRYPTO_CREATE_WITH_ALIAS_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.AUTO_RENEW_TARGET_TYPES, AS_ENTITY_TYPES),
                    entry(PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN, AS_INT),
                    entry(PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE, AS_INT),
                    entry(PropertyNames.AUTO_RENEW_GRACE_PERIOD, AS_LONG),
                    entry(PropertyNames.AUTO_RENEW_GRANT_FREE_RENEWALS, AS_BOOLEAN),
                    entry(PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, AS_LONG),
                    entry(PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, AS_LONG),
                    entry(PropertyNames.NETTY_MODE, AS_PROFILE),
                    entry(PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES, AS_INT),
                    entry(PropertyNames.NETTY_START_RETRIES, AS_INT),
                    entry(PropertyNames.NETTY_START_RETRY_INTERVAL_MS, AS_LONG),
                    entry(PropertyNames.BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV, AS_INT),
                    entry(PropertyNames.BOOTSTRAP_RATES_CURRENT_CENT_EQUIV, AS_INT),
                    entry(PropertyNames.BOOTSTRAP_RATES_CURRENT_EXPIRY, AS_LONG),
                    entry(PropertyNames.BOOTSTRAP_RATES_NEXT_HBAR_EQUIV, AS_INT),
                    entry(PropertyNames.BOOTSTRAP_RATES_NEXT_CENT_EQUIV, AS_INT),
                    entry(PropertyNames.BOOTSTRAP_RATES_NEXT_EXPIRY, AS_LONG),
                    entry(PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY, AS_LONG),
                    entry(PropertyNames.FEES_MIN_CONGESTION_PERIOD, AS_INT),
                    entry(PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER, AS_INT),
                    entry(
                            PropertyNames.FEES_PERCENT_CONGESTION_MULTIPLIERS,
                            AS_CONGESTION_MULTIPLIERS),
                    entry(
                            PropertyNames.FEES_PERCENT_UTILIZATION_SCALE_FACTORS,
                            AS_ENTITY_SCALE_FACTORS),
                    entry(PropertyNames.LEDGER_CHANGE_HIST_MEM_SECS, AS_INT),
                    entry(PropertyNames.LEDGER_XFER_BAL_CHANGES_MAX_LEN, AS_INT),
                    entry(PropertyNames.LEDGER_FUNDING_ACCOUNT, AS_LONG),
                    entry(PropertyNames.LEDGER_NUM_SYSTEM_ACCOUNTS, AS_INT),
                    entry(PropertyNames.LEDGER_TRANSFERS_MAX_LEN, AS_INT),
                    entry(PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN, AS_INT),
                    entry(PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN, AS_INT),
                    entry(PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT, AS_LONG),
                    entry(PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, AS_INT),
                    entry(PropertyNames.LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT, AS_INT),
                    entry(PropertyNames.ISS_RESET_PERIOD, AS_INT),
                    entry(PropertyNames.ISS_ROUNDS_TO_LOG, AS_INT),
                    entry(PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW, AS_INT),
                    entry(PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS, AS_INT),
                    entry(PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE, AS_LONG),
                    entry(PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE, AS_LONG),
                    entry(PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE, AS_LONG),
                    entry(PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME, AS_LONG),
                    entry(PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT, AS_LONG),
                    entry(PropertyNames.SCHEDULING_MAX_NUM, AS_LONG),
                    entry(PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT, AS_INT),
                    entry(PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT, AS_INT),
                    entry(PropertyNames.STAKING_PERIOD_MINS, AS_LONG),
                    entry(PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS, AS_INT),
                    entry(PropertyNames.STAKING_REQUIRE_MIN_STAKE_TO_REWARD, AS_BOOLEAN),
                    entry(PropertyNames.STAKING_REWARD_RATE, AS_LONG),
                    entry(PropertyNames.STAKING_START_THRESH, AS_LONG),
                    entry(PropertyNames.TOKENS_MAX_AGGREGATE_RELS, AS_LONG),
                    entry(PropertyNames.TOKENS_STORE_RELS_ON_DISK, AS_BOOLEAN),
                    entry(PropertyNames.TOKENS_MAX_NUM, AS_LONG),
                    entry(PropertyNames.TOKENS_MAX_PER_ACCOUNT, AS_INT),
                    entry(PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY, AS_INT),
                    entry(PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED, AS_INT),
                    entry(PropertyNames.TOKENS_MAX_CUSTOM_FEE_DEPTH, AS_INT),
                    entry(PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES, AS_INT),
                    entry(PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES, AS_INT),
                    entry(PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES, AS_INT),
                    entry(PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN, AS_INT),
                    entry(
                            PropertyNames.TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR,
                            AS_THROTTLE_SCALE_FACTOR),
                    entry(PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE, AS_INT),
                    entry(PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT, AS_INT),
                    entry(PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS, AS_LONG),
                    entry(PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE, AS_LONG),
                    entry(PropertyNames.TOKENS_NFTS_USE_TREASURY_WILD_CARDS, AS_BOOLEAN),
                    entry(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE, AS_BOOLEAN),
                    entry(PropertyNames.TOPICS_MAX_NUM, AS_LONG),
                    entry(PropertyNames.CONTRACTS_MAX_NUM, AS_LONG),
                    entry(PropertyNames.CONTRACTS_KNOWN_BLOCK_HASH, AS_KNOWN_BLOCK_VALUES),
                    entry(PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES, AS_INT),
                    entry(PropertyNames.CONTRACTS_ALLOW_CREATE2, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_ALLOW_AUTO_ASSOCIATIONS, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_DEFAULT_LIFETIME, AS_LONG),
                    entry(PropertyNames.CONTRACTS_MAX_GAS_PER_SEC, AS_LONG),
                    entry(PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_ENFORCE_CREATION_THROTTLE, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_REFERENCE_SLOT_LIFETIME, AS_LONG),
                    entry(PropertyNames.CONTRACTS_FREE_STORAGE_TIER_LIMIT, AS_INT),
                    entry(PropertyNames.CONTRACTS_MAX_KV_PAIRS_AGGREGATE, AS_LONG),
                    entry(PropertyNames.CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL, AS_INT),
                    entry(PropertyNames.CONTRACTS_CHAIN_ID, AS_INT),
                    entry(PropertyNames.CONTRACTS_SIDECARS, AS_SIDECARS),
                    entry(PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, AS_INT),
                    entry(PropertyNames.CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT, AS_LONG),
                    entry(PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST, AS_LONG),
                    entry(PropertyNames.CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST, AS_LONG),
                    entry(PropertyNames.CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE, AS_BOOLEAN),
                    entry(
                            PropertyNames.CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED,
                            AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_THROTTLE_THROTTLE_BY_GAS, AS_BOOLEAN),
                    entry(PropertyNames.CONTRACTS_EVM_VERSION, AS_STRING),
                    entry(PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION, AS_BOOLEAN),
                    entry(PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT, AS_INT),
                    entry(PropertyNames.RATES_MIDNIGHT_CHECK_INTERVAL, AS_LONG),
                    entry(PropertyNames.SIGS_EXPAND_FROM_IMMUTABLE_STATE, AS_BOOLEAN),
                    entry(PropertyNames.SCHEDULING_LONG_TERM_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.SCHEDULING_MAX_TXN_PER_SEC, AS_LONG),
                    entry(PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS, AS_LONG),
                    entry(PropertyNames.SCHEDULING_WHITE_LIST, AS_FUNCTIONS),
                    entry(PropertyNames.STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS, AS_NODE_STAKE_RATIOS),
                    entry(PropertyNames.STAKING_IS_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR, AS_LONG),
                    entry(PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS, AS_LONG),
                    entry(PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS, AS_LONG),
                    entry(PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS, AS_LONG),
                    entry(PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS, AS_DOUBLE),
                    entry(PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS, AS_DOUBLE),
                    entry(PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED, AS_INT),
                    entry(PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS, AS_LONG),
                    entry(PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS, AS_LONG),
                    entry(PropertyNames.TOKENS_NFTS_ARE_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE, AS_CS_STRINGS),
                    entry(PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE, AS_CS_STRINGS),
                    entry(PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK, AS_INT),
                    entry(PropertyNames.HEDERA_ALLOWANCES_MAX_TXN_LIMIT, AS_INT),
                    entry(PropertyNames.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, AS_INT),
                    entry(PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.ENTITIES_LIMIT_TOKEN_ASSOCIATIONS, AS_BOOLEAN),
                    entry(PropertyNames.UTIL_PRNG_IS_ENABLED, AS_BOOLEAN),
                    entry(PropertyNames.TOKENS_AUTO_CREATIONS_ENABLED, AS_BOOLEAN));
}
