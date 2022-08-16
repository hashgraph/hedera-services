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

import static com.hedera.services.context.properties.PropUtils.loadOverride;
import static com.hedera.services.context.properties.PropertyNames.*;
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

    void ensureProps() throws IllegalStateException {
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
                    BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE,
                    BOOTSTRAP_GENESIS_PUBLIC_KEY,
                    BOOTSTRAP_HAPI_PERMISSIONS_PATH,
                    BOOTSTRAP_NETWORK_PROPERTIES_PATH,
                    BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV,
                    BOOTSTRAP_RATES_CURRENT_CENT_EQUIV,
                    BOOTSTRAP_RATES_CURRENT_EXPIRY,
                    BOOTSTRAP_RATES_NEXT_HBAR_EQUIV,
                    BOOTSTRAP_RATES_NEXT_CENT_EQUIV,
                    BOOTSTRAP_RATES_NEXT_EXPIRY,
                    BOOTSTRAP_SYSTEM_ENTITY_EXPIRY,
                    BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE);

    private static final Set<String> GLOBAL_STATIC_PROPS =
            Set.of(
                    ACCOUNTS_ADDRESS_BOOK_ADMIN,
                    ACCOUNTS_EXCHANGE_RATES_ADMIN,
                    ACCOUNTS_FEE_SCHEDULE_ADMIN,
                    ACCOUNTS_FREEZE_ADMIN,
                    ACCOUNTS_NODE_REWARD_ACCOUNT,
                    ACCOUNTS_STAKING_REWARD_ACCOUNT,
                    ACCOUNTS_SYSTEM_ADMIN,
                    ACCOUNTS_SYSTEM_DELETE_ADMIN,
                    ACCOUNTS_SYSTEM_UNDELETE_ADMIN,
                    ACCOUNTS_TREASURY,
                    ENTITIES_MAX_LIFETIME,
                    ENTITIES_SYSTEM_DELETABLE,
                    FILES_ADDRESS_BOOK,
                    FILES_NETWORK_PROPERTIES,
                    FILES_EXCHANGE_RATES,
                    FILES_FEE_SCHEDULES,
                    FILES_HAPI_PERMISSIONS,
                    FILES_NODE_DETAILS,
                    FILES_SOFTWARE_UPDATE_RANGE,
                    FILES_THROTTLE_DEFINITIONS,
                    HEDERA_FIRST_USER_ENTITY,
                    HEDERA_REALM,
                    HEDERA_SHARD,
                    LEDGER_NUM_SYSTEM_ACCOUNTS,
                    LEDGER_TOTAL_TINY_BAR_FLOAT,
                    LEDGER_ID,
                    STAKING_PERIOD_MINS,
                    STAKING_REWARD_HISTORY_NUM_STORED_PERIODS);

    static final Set<String> GLOBAL_DYNAMIC_PROPS =
            Set.of(
                    ACCOUNTS_MAX_NUMBER,
                    AUTO_CREATION_ENABLED,
                    BALANCES_EXPORT_DIR_PATH,
                    BALANCES_EXPORT_ENABLED,
                    BALANCES_EXPORT_PERIOD_SECS,
                    BALANCES_EXPORT_TOKEN_BALANCES,
                    BALANCES_NODE_BALANCE_WARN_THRESHOLD,
                    CACHE_RECORDS_TTL,
                    CONTRACTS_DEFAULT_LIFETIME,
                    CONTRACTS_KNOWN_BLOCK_HASH,
                    CONTRACTS_LOCAL_CALL_EST_RET_BYTES,
                    CONTRACTS_ALLOW_CREATE2,
                    CONTRACTS_ALLOW_AUTO_ASSOCIATIONS,
                    CONTRACTS_MAX_GAS_PER_SEC,
                    CONTRACTS_MAX_KV_PAIRS_AGGREGATE,
                    CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL,
                    CONTRACTS_MAX_NUM,
                    CONTRACTS_CHAIN_ID,
                    CONTRACTS_SIDECARS,
                    CONTRACTS_THROTTLE_THROTTLE_BY_GAS,
                    CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT,
                    CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT,
                    CONTRACTS_REDIRECT_TOKEN_CALLS,
                    CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST,
                    CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST,
                    CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS,
                    CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE,
                    FILES_MAX_NUM,
                    FILES_MAX_SIZE_KB,
                    HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB,
                    FEES_MIN_CONGESTION_PERIOD,
                    FEES_PERCENT_CONGESTION_MULTIPLIERS,
                    FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER,
                    HEDERA_TXN_MAX_MEMO_UTF8_BYTES,
                    HEDERA_TXN_MAX_VALID_DURATION,
                    HEDERA_TXN_MIN_VALID_DURATION,
                    HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS,
                    HEDERA_RECORD_STREAM_RECORD_FILE_VERSION,
                    HEDERA_RECORD_STREAM_SIG_FILE_VERSION,
                    AUTO_REMOVE_MAX_PURGED_KV_PAIRS_PER_TOUCH,
                    AUTO_REMOVE_MAX_RETURNED_NFTS_PER_TOUCH,
                    AUTO_RENEW_TARGET_TYPES,
                    AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN,
                    AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE,
                    AUTO_RENEW_GRACE_PERIOD,
                    LEDGER_CHANGE_HIST_MEM_SECS,
                    LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION,
                    LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                    LEDGER_XFER_BAL_CHANGES_MAX_LEN,
                    LEDGER_FUNDING_ACCOUNT,
                    LEDGER_TRANSFERS_MAX_LEN,
                    LEDGER_TOKEN_TRANSFERS_MAX_LEN,
                    LEDGER_NFT_TRANSFERS_MAX_LEN,
                    LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT,
                    LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS,
                    RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT,
                    RATES_MIDNIGHT_CHECK_INTERVAL,
                    SCHEDULING_LONG_TERM_ENABLED,
                    SCHEDULING_MAX_TXN_PER_SEC,
                    SCHEDULING_MAX_NUM,
                    SCHEDULING_MAX_EXPIRATION_FUTURE_SECS,
                    SCHEDULING_WHITE_LIST,
                    SIGS_EXPAND_FROM_IMMUTABLE_STATE,
                    STAKING_FEES_NODE_REWARD_PERCENT,
                    STAKING_FEES_STAKING_REWARD_PERCENT,
                    STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS,
                    STAKING_IS_ENABLED,
                    STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR,
                    STAKING_REQUIRE_MIN_STAKE_TO_REWARD,
                    STAKING_REWARD_RATE,
                    STAKING_START_THRESH,
                    TOKENS_MAX_AGGREGATE_RELS,
                    TOKENS_MAX_NUM,
                    TOKENS_MAX_RELS_PER_INFO_QUERY,
                    TOKENS_MAX_PER_ACCOUNT,
                    TOKENS_MAX_SYMBOL_UTF8_BYTES,
                    TOKENS_MAX_TOKEN_NAME_UTF8_BYTES,
                    TOKENS_MAX_CUSTOM_FEES_ALLOWED,
                    TOKENS_MAX_CUSTOM_FEE_DEPTH,
                    TOKENS_NFTS_ARE_ENABLED,
                    TOKENS_NFTS_MAX_METADATA_BYTES,
                    TOKENS_NFTS_MAX_BATCH_SIZE_BURN,
                    TOKENS_NFTS_MAX_BATCH_SIZE_WIPE,
                    TOKENS_NFTS_MAX_BATCH_SIZE_MINT,
                    TOKENS_NFTS_MAX_ALLOWED_MINTS,
                    TOKENS_NFTS_MAX_QUERY_RANGE,
                    TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR,
                    TOPICS_MAX_NUM,
                    TOKENS_NFTS_USE_TREASURY_WILD_CARDS,
                    CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED,
                    CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS,
                    CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS,
                    UPGRADE_ARTIFACTS_PATH,
                    HEDERA_ALLOWANCES_MAX_TXN_LIMIT,
                    HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT,
                    HEDERA_ALLOWANCES_IS_ENABLED,
                    ENTITIES_LIMIT_TOKEN_ASSOCIATIONS,
                    UTIL_PRNG_IS_ENABLED,
                    HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION);

    static final Set<String> NODE_PROPS =
            Set.of(
                    "dev.onlyDefaultNodeListens",
                    "dev.defaultListeningNodeAccount",
                    "grpc.port",
                    "grpc.tlsPort",
                    "hedera.accountsExportPath",
                    "hedera.exportAccountsOnStartup",
                    "hedera.prefetch.queueCapacity",
                    "hedera.prefetch.threadPoolSize",
                    "hedera.prefetch.codeCacheTtlSecs",
                    "hedera.profiles.active",
                    "hedera.recordStream.isEnabled",
                    "hedera.recordStream.logDir",
                    "hedera.recordStream.sidecarDir",
                    "hedera.recordStream.logPeriod",
                    "hedera.recordStream.queueCapacity",
                    "iss.resetPeriod",
                    "iss.roundsToLog",
                    "netty.mode",
                    "netty.prod.flowControlWindow",
                    "netty.prod.maxConcurrentCalls",
                    "netty.prod.maxConnectionAge",
                    "netty.prod.maxConnectionAgeGrace",
                    "netty.prod.maxConnectionIdle",
                    "netty.prod.keepAliveTime",
                    "netty.prod.keepAliveTimeout",
                    "netty.startRetries",
                    "netty.startRetryIntervalMs",
                    "netty.tlsCrt.path",
                    "netty.tlsKey.path",
                    "queries.blob.lookupRetries",
                    "stats.consThrottlesToSample",
                    "stats.hapiThrottlesToSample",
                    "stats.executionTimesToTrack",
                    "stats.entityUtils.gaugeUpdateIntervalMs",
                    "stats.hapiOps.speedometerUpdateIntervalMs",
                    "stats.throttleUtils.gaugeUpdateIntervalMs",
                    "stats.runningAvgHalfLifeSecs",
                    "stats.speedometerHalfLifeSecs");

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
                    entry(ACCOUNTS_ADDRESS_BOOK_ADMIN, AS_LONG),
                    entry(ACCOUNTS_EXCHANGE_RATES_ADMIN, AS_LONG),
                    entry(ACCOUNTS_FEE_SCHEDULE_ADMIN, AS_LONG),
                    entry(ACCOUNTS_FREEZE_ADMIN, AS_LONG),
                    entry(ACCOUNTS_MAX_NUMBER, AS_LONG),
                    entry(ACCOUNTS_NODE_REWARD_ACCOUNT, AS_LONG),
                    entry(ACCOUNTS_STAKING_REWARD_ACCOUNT, AS_LONG),
                    entry(ACCOUNTS_SYSTEM_ADMIN, AS_LONG),
                    entry(ACCOUNTS_SYSTEM_DELETE_ADMIN, AS_LONG),
                    entry(ACCOUNTS_SYSTEM_UNDELETE_ADMIN, AS_LONG),
                    entry(ACCOUNTS_TREASURY, AS_LONG),
                    entry(BALANCES_EXPORT_ENABLED, AS_BOOLEAN),
                    entry(BALANCES_EXPORT_PERIOD_SECS, AS_INT),
                    entry(BALANCES_NODE_BALANCE_WARN_THRESHOLD, AS_LONG),
                    entry(CACHE_RECORDS_TTL, AS_INT),
                    entry("dev.onlyDefaultNodeListens", AS_BOOLEAN),
                    entry(BALANCES_EXPORT_TOKEN_BALANCES, AS_BOOLEAN),
                    entry(ENTITIES_MAX_LIFETIME, AS_LONG),
                    entry(ENTITIES_SYSTEM_DELETABLE, AS_ENTITY_TYPES),
                    entry(FILES_ADDRESS_BOOK, AS_LONG),
                    entry(FILES_MAX_NUM, AS_LONG),
                    entry(FILES_MAX_SIZE_KB, AS_INT),
                    entry(FILES_NETWORK_PROPERTIES, AS_LONG),
                    entry(FILES_EXCHANGE_RATES, AS_LONG),
                    entry(FILES_FEE_SCHEDULES, AS_LONG),
                    entry(FILES_HAPI_PERMISSIONS, AS_LONG),
                    entry(FILES_NODE_DETAILS, AS_LONG),
                    entry(FILES_SOFTWARE_UPDATE_RANGE, AS_ENTITY_NUM_RANGE),
                    entry(FILES_THROTTLE_DEFINITIONS, AS_LONG),
                    entry("grpc.port", AS_INT),
                    entry("grpc.tlsPort", AS_INT),
                    entry("hedera.exportAccountsOnStartup", AS_BOOLEAN),
                    entry("hedera.firstUserEntity", AS_LONG),
                    entry("hedera.prefetch.queueCapacity", AS_INT),
                    entry("hedera.prefetch.threadPoolSize", AS_INT),
                    entry("hedera.prefetch.codeCacheTtlSecs", AS_INT),
                    entry("hedera.profiles.active", AS_PROFILE),
                    entry("hedera.realm", AS_LONG),
                    entry("hedera.recordStream.logPeriod", AS_LONG),
                    entry("hedera.recordStream.isEnabled", AS_BOOLEAN),
                    entry(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION, AS_INT),
                    entry(HEDERA_RECORD_STREAM_SIG_FILE_VERSION, AS_INT),
                    entry("hedera.recordStream.queueCapacity", AS_INT),
                    entry(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB, AS_INT),
                    entry("hedera.recordStream.enableTraceabilityMigration", AS_BOOLEAN),
                    entry(HEDERA_SHARD, AS_LONG),
                    entry(HEDERA_TXN_MAX_MEMO_UTF8_BYTES, AS_INT),
                    entry(HEDERA_TXN_MAX_VALID_DURATION, AS_LONG),
                    entry(HEDERA_TXN_MIN_VALID_DURATION, AS_LONG),
                    entry(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS, AS_INT),
                    entry(AUTO_CREATION_ENABLED, AS_BOOLEAN),
                    entry(AUTO_REMOVE_MAX_PURGED_KV_PAIRS_PER_TOUCH, AS_INT),
                    entry(AUTO_REMOVE_MAX_RETURNED_NFTS_PER_TOUCH, AS_INT),
                    entry("autoRenew.targetTypes", AS_ENTITY_TYPES),
                    entry("autorenew.numberOfEntitiesToScan", AS_INT),
                    entry(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE, AS_INT),
                    entry(AUTO_RENEW_GRACE_PERIOD, AS_LONG),
                    entry(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, AS_LONG),
                    entry(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, AS_LONG),
                    entry("netty.mode", AS_PROFILE),
                    entry("queries.blob.lookupRetries", AS_INT),
                    entry("netty.startRetries", AS_INT),
                    entry("netty.startRetryIntervalMs", AS_LONG),
                    entry(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV, AS_INT),
                    entry(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV, AS_INT),
                    entry(BOOTSTRAP_RATES_CURRENT_EXPIRY, AS_LONG),
                    entry(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV, AS_INT),
                    entry(BOOTSTRAP_RATES_NEXT_CENT_EQUIV, AS_INT),
                    entry(BOOTSTRAP_RATES_NEXT_EXPIRY, AS_LONG),
                    entry(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY, AS_LONG),
                    entry(FEES_MIN_CONGESTION_PERIOD, AS_INT),
                    entry(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER, AS_INT),
                    entry(FEES_PERCENT_CONGESTION_MULTIPLIERS, AS_CONGESTION_MULTIPLIERS),
                    entry(LEDGER_CHANGE_HIST_MEM_SECS, AS_INT),
                    entry(LEDGER_XFER_BAL_CHANGES_MAX_LEN, AS_INT),
                    entry(LEDGER_FUNDING_ACCOUNT, AS_LONG),
                    entry(LEDGER_NUM_SYSTEM_ACCOUNTS, AS_INT),
                    entry(LEDGER_TRANSFERS_MAX_LEN, AS_INT),
                    entry(LEDGER_TOKEN_TRANSFERS_MAX_LEN, AS_INT),
                    entry(LEDGER_NFT_TRANSFERS_MAX_LEN, AS_INT),
                    entry(LEDGER_TOTAL_TINY_BAR_FLOAT, AS_LONG),
                    entry(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, AS_INT),
                    entry(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT, AS_INT),
                    entry("iss.resetPeriod", AS_INT),
                    entry("iss.roundsToLog", AS_INT),
                    entry("netty.prod.flowControlWindow", AS_INT),
                    entry("netty.prod.maxConcurrentCalls", AS_INT),
                    entry("netty.prod.maxConnectionAge", AS_LONG),
                    entry("netty.prod.maxConnectionAgeGrace", AS_LONG),
                    entry("netty.prod.maxConnectionIdle", AS_LONG),
                    entry("netty.prod.keepAliveTime", AS_LONG),
                    entry("netty.prod.keepAliveTimeout", AS_LONG),
                    entry(SCHEDULING_MAX_NUM, AS_LONG),
                    entry(STAKING_FEES_NODE_REWARD_PERCENT, AS_INT),
                    entry(STAKING_FEES_STAKING_REWARD_PERCENT, AS_INT),
                    entry(STAKING_PERIOD_MINS, AS_LONG),
                    entry(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS, AS_INT),
                    entry(STAKING_REQUIRE_MIN_STAKE_TO_REWARD, AS_BOOLEAN),
                    entry(STAKING_REWARD_RATE, AS_LONG),
                    entry(STAKING_START_THRESH, AS_LONG),
                    entry(TOKENS_MAX_AGGREGATE_RELS, AS_LONG),
                    entry(TOKENS_MAX_NUM, AS_LONG),
                    entry(TOKENS_MAX_PER_ACCOUNT, AS_INT),
                    entry(TOKENS_MAX_RELS_PER_INFO_QUERY, AS_INT),
                    entry(TOKENS_MAX_CUSTOM_FEES_ALLOWED, AS_INT),
                    entry(TOKENS_MAX_CUSTOM_FEE_DEPTH, AS_INT),
                    entry(TOKENS_MAX_SYMBOL_UTF8_BYTES, AS_INT),
                    entry(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES, AS_INT),
                    entry(TOKENS_NFTS_MAX_METADATA_BYTES, AS_INT),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_BURN, AS_INT),
                    entry(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR, AS_THROTTLE_SCALE_FACTOR),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE, AS_INT),
                    entry(TOKENS_NFTS_MAX_BATCH_SIZE_MINT, AS_INT),
                    entry(TOKENS_NFTS_MAX_ALLOWED_MINTS, AS_LONG),
                    entry(TOKENS_NFTS_MAX_QUERY_RANGE, AS_LONG),
                    entry(TOKENS_NFTS_USE_TREASURY_WILD_CARDS, AS_BOOLEAN),
                    entry(TOPICS_MAX_NUM, AS_LONG),
                    entry(CONTRACTS_MAX_NUM, AS_LONG),
                    entry(CONTRACTS_KNOWN_BLOCK_HASH, AS_KNOWN_BLOCK_VALUES),
                    entry(CONTRACTS_LOCAL_CALL_EST_RET_BYTES, AS_INT),
                    entry(CONTRACTS_ALLOW_CREATE2, AS_BOOLEAN),
                    entry(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS, AS_BOOLEAN),
                    entry(CONTRACTS_DEFAULT_LIFETIME, AS_LONG),
                    entry(CONTRACTS_MAX_GAS_PER_SEC, AS_LONG),
                    entry(CONTRACTS_MAX_KV_PAIRS_AGGREGATE, AS_LONG),
                    entry(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL, AS_INT),
                    entry(CONTRACTS_CHAIN_ID, AS_INT),
                    entry(CONTRACTS_SIDECARS, AS_SIDECARS),
                    entry(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, AS_INT),
                    entry(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT, AS_LONG),
                    entry(CONTRACTS_REDIRECT_TOKEN_CALLS, AS_BOOLEAN),
                    entry(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST, AS_LONG),
                    entry(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST, AS_LONG),
                    entry(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS, AS_BOOLEAN),
                    entry(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE, AS_BOOLEAN),
                    entry(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, AS_BOOLEAN),
                    entry(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT, AS_INT),
                    entry(RATES_MIDNIGHT_CHECK_INTERVAL, AS_LONG),
                    entry(SIGS_EXPAND_FROM_IMMUTABLE_STATE, AS_BOOLEAN),
                    entry(SCHEDULING_LONG_TERM_ENABLED, AS_BOOLEAN),
                    entry(SCHEDULING_MAX_TXN_PER_SEC, AS_LONG),
                    entry(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS, AS_LONG),
                    entry(SCHEDULING_WHITE_LIST, AS_FUNCTIONS),
                    entry(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS, AS_NODE_STAKE_RATIOS),
                    entry(STAKING_IS_ENABLED, AS_BOOLEAN),
                    entry(STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR, AS_LONG),
                    entry("stats.entityUtils.gaugeUpdateIntervalMs", AS_LONG),
                    entry("stats.hapiOps.speedometerUpdateIntervalMs", AS_LONG),
                    entry("stats.throttleUtils.gaugeUpdateIntervalMs", AS_LONG),
                    entry("stats.runningAvgHalfLifeSecs", AS_DOUBLE),
                    entry("stats.speedometerHalfLifeSecs", AS_DOUBLE),
                    entry(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED, AS_INT),
                    entry(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS, AS_LONG),
                    entry(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS, AS_LONG),
                    entry(TOKENS_NFTS_ARE_ENABLED, AS_BOOLEAN),
                    entry("stats.consThrottlesToSample", AS_CS_STRINGS),
                    entry("stats.hapiThrottlesToSample", AS_CS_STRINGS),
                    entry("stats.executionTimesToTrack", AS_INT),
                    entry(HEDERA_ALLOWANCES_MAX_TXN_LIMIT, AS_INT),
                    entry(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, AS_INT),
                    entry(HEDERA_ALLOWANCES_IS_ENABLED, AS_BOOLEAN),
                    entry(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS, AS_BOOLEAN),
                    entry(UTIL_PRNG_IS_ENABLED, AS_BOOLEAN));
}
