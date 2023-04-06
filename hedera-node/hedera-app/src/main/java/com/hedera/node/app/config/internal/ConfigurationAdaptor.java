/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.config.internal;

import static com.hedera.node.app.spi.config.PropertyNames.LEDGER_FUNDING_ACCOUNT;

import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.GlobalConfig;
import com.hedera.node.app.spi.config.GlobalDynamicConfig;
import com.hedera.node.app.spi.config.NodeConfig;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.node.app.spi.config.PropertyNames;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adaptor for the configuration functionality. This class will be removed in future once the full services layer is
 * refactored to use the "real" config api from the platform. In that case the {@link Configuration} implementation from
 * the platform will be used.
 *
 * <p>This implementation is backed by a {@link PropertySource} instance and all calls will be
 * forwarded to that instance.
 */
@Deprecated(forRemoval = true)
public class ConfigurationAdaptor implements Configuration {

    private final PropertySource propertySource;

    private final HederaNumbers hederaNums;

    private final NodeConfig nodeConfig;

    private final GlobalConfig globalConfig;

    private final GlobalDynamicConfig globalDynamicConfig;

    public ConfigurationAdaptor(@NonNull final PropertySource propertySource) {
        this.propertySource = Objects.requireNonNull(propertySource, "propertySource");
        this.hederaNums = new HederaNumbers(propertySource);
        nodeConfig = createNodeConfig();
        globalConfig = createGlobalConfig();
        globalDynamicConfig = createGlobalDynamicConfig();
    }

    @Override
    public Stream<String> getPropertyNames() {
        return propertySource.allPropertyNames().stream();
    }

    @Override
    public boolean exists(final String name) {
        return propertySource.containsProperty(name);
    }

    @Override
    public String getValue(final String name) throws NoSuchElementException {
        if (exists(name)) {
            return propertySource.getRawValue(name);
        }
        throw new NoSuchElementException(exceptionMessagePropertyDoesNotExist(name));
    }

    @Override
    public String getValue(final String name, final String defaultValue) {
        if (exists(name)) {
            return getValue(name);
        }
        return defaultValue;
    }

    @Override
    public <T> T getValue(final String name, final Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        if (exists(name)) {
            return propertySource.getTypedProperty(type, name);
        }
        throw new NoSuchElementException(exceptionMessagePropertyDoesNotExist(name));
    }

    @Override
    public <T> T getValue(final String name, final Class<T> type, final T defaultValue)
            throws IllegalArgumentException {
        if (exists(name)) {
            return getValue(name, type);
        }
        return defaultValue;
    }

    @Override
    public List<String> getValues(final String name) {
        if (exists(name)) {
            return propertySource.getTypedProperty(List.class, name);
        }
        throw new NoSuchElementException(exceptionMessagePropertyDoesNotExist(name));
    }

    @Override
    public List<String> getValues(final String name, final List<String> defaultValues) {
        if (exists(name)) {
            return getValues(name);
        }
        return defaultValues;
    }

    @Override
    public <T> List<T> getValues(final String name, final Class<T> type)
            throws NoSuchElementException, IllegalArgumentException {
        if (exists(name)) {
            return propertySource.getTypedProperty(List.class, name);
        }
        throw new NoSuchElementException(exceptionMessagePropertyDoesNotExist(name));
    }

    @Override
    public <T> List<T> getValues(final String name, final Class<T> type, final List<T> defaultValues)
            throws IllegalArgumentException {
        if (exists(name)) {
            return getValues(name, type);
        }
        return defaultValues;
    }

    /**
     * This implementation only supports {@link GlobalConfig} and {@link NodeConfig} as config data records.
     *
     * @param type {@link GlobalConfig} or {@link NodeConfig}, otherwise a {@link IllegalArgumentException} will be
     *             thrown
     * @param <T>  type, must be {@link GlobalConfig} or {@link NodeConfig}
     * @return the {@link GlobalConfig} or {@link NodeConfig} instance
     * @see Configuration#getConfigData(Class)
     */
    @Override
    public <T extends Record> T getConfigData(final Class<T> type) {
        if (Objects.equals(type, GlobalConfig.class)) {
            return (T) globalConfig;
        }
        if (Objects.equals(type, NodeConfig.class)) {
            return (T) nodeConfig;
        }
        throw new IllegalArgumentException("Config data type '" + type.getName() + "' not defined");
    }

    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return Set.of(GlobalConfig.class, NodeConfig.class);
    }

    /**
     * Since we do not depend on the real config implementation we need to create the config data records "by hand".
     *
     * @return a new NodeConfig instance
     */
    private NodeConfig createNodeConfig() {
        return new NodeConfig(
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_PORT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_TLS_PORT),
                propertySource.getTypedProperty(
                        Long.class, PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(Long.class, PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(
                        Long.class, PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS),
                propertySource.getTypedProperty(Profile.class, PropertyNames.HEDERA_PROFILES_ACTIVE),
                propertySource.getTypedProperty(Double.class, PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS),
                propertySource.getTypedProperty(Double.class, PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR),
                propertySource.getTypedProperty(Long.class, PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY),
                propertySource.getTypedProperty(Integer.class, PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME),
                propertySource.getTypedProperty(String.class, PropertyNames.NETTY_TLS_CERT_PATH),
                propertySource.getTypedProperty(String.class, PropertyNames.NETTY_TLS_KEY_PATH),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW),
                propertySource.getTypedProperty(String.class, PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP),
                propertySource.getTypedProperty(Profile.class, PropertyNames.NETTY_MODE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.NETTY_START_RETRIES),
                propertySource.getTypedProperty(Long.class, PropertyNames.NETTY_START_RETRY_INTERVAL_MS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK),
                propertySource.getTypedProperty(Integer.class, PropertyNames.ISS_RESET_PERIOD),
                propertySource.getTypedProperty(Integer.class, PropertyNames.ISS_ROUNDS_TO_LOG),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS),
                propertySource.getTypedProperty(List.class, PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE),
                propertySource.getTypedProperty(List.class, PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE),
                propertySource.getTypedProperty(String.class, PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_WORKFLOWS_PORT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.GRPC_WORKFLOWS_TLS_PORT));
    }

    /**
     * Since we do not depend on the real config implementation we need to create the config data * records "by hand".
     *
     * @return a new GlobalConfig instance
     */
    private GlobalConfig createGlobalConfig() {
        return new GlobalConfig(propertySource.getTypedProperty(Set.class, PropertyNames.WORKFLOWS_ENABLED));
    }


    private GlobalDynamicConfig createGlobalDynamicConfig() {

        final AccountID fundingAccount = AccountID.newBuilder()
                .setShardNum(hederaNums.shard())
                .setRealmNum(hederaNums.realm())
                .setAccountNum(propertySource.getLongProperty(LEDGER_FUNDING_ACCOUNT))
                .build();

        return new GlobalDynamicConfig(
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE),
                propertySource.getTypedProperty(Long.class, PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.TOKENS_NFTS_USE_TREASURY_WILDCARDS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_PER_ACCOUNT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES),
                propertySource.getTypedProperty(Integer.class, PropertyNames.FILES_MAX_SIZE_KB),
                propertySource.getTypedProperty(Integer.class, PropertyNames.CACHE_RECORDS_TTL),
                propertySource.getTypedProperty(Integer.class, PropertyNames.BALANCES_EXPORT_PERIOD_SECS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT),
                propertySource.getTypedProperty(Long.class, PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD),
                propertySource.getTypedProperty(String.class, PropertyNames.BALANCES_EXPORT_DIR_PATH),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.BALANCES_EXPORT_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES),
                fundingAccount,
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_TRANSFERS_MAX_LEN),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES),
                propertySource.getTypedProperty(Long.class, PropertyNames.HEDERA_TXN_MAX_VALID_DURATION),
                propertySource.getTypedProperty(Long.class, PropertyNames.HEDERA_TXN_MIN_VALID_DURATION),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_MAX_GAS_PER_SEC),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_DEFAULT_LIFETIME),
                propertySource.getTypedProperty(String.class, PropertyNames.CONTRACTS_EVM_VERSION),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION),
                propertySource.getTypedProperty(Integer.class, PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER),
                propertySource.getTypedProperty(Integer.class, PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN),
                propertySource.getTypedProperty(Integer.class,
                        PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE),
                propertySource.getTypedProperty(Long.class, PropertyNames.AUTO_RENEW_GRACE_PERIOD),
                propertySource.getTypedProperty(Long.class, PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION),
                propertySource.getTypedProperty(Long.class, PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION),
                propertySource.getTypedProperty(Integer.class, PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.SCHEDULING_LONG_TERM_ENABLED),
                propertySource.getTypedProperty(Long.class, PropertyNames.SCHEDULING_MAX_TXN_PER_SEC),
                propertySource.getTypedProperty(Long.class, PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS),
                propertySource.getTypedProperty(Set.class, PropertyNames.SCHEDULING_WHITE_LIST),
                propertySource.getTypedProperty(Set.class, PropertyNames.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.FEES_MIN_CONGESTION_PERIOD),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.TOKENS_NFTS_ARE_ENABLED),
                propertySource.getTypedProperty(Long.class, PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_XFER_BAL_CHANGES_MAX_LEN),
                propertySource.getTypedProperty(Integer.class, PropertyNames.TOKENS_MAX_CUSTOM_FEE_DEPTH),
                propertySource.getTypedProperty(String.class, PropertyNames.UPGRADE_ARTIFACTS_PATH),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_THROTTLE_THROTTLE_BY_GAS),
                propertySource.getTypedProperty(Integer.class, PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_CHANGE_HIST_MEM_SECS),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.AUTO_CREATION_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.SIGS_EXPAND_FROM_IMMUTABLE_STATE),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_MAX_KV_PAIRS_AGGREGATE),
                propertySource.getTypedProperty(Integer.class, PropertyNames.CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL),
                propertySource.getTypedProperty(Integer.class, PropertyNames.LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_ALLOWANCES_MAX_TXN_LIMIT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT),
                propertySource.getTypedProperty(Boolean.class,
                        PropertyNames.CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_ALLOW_CREATE2),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.ENTITIES_LIMIT_TOKEN_ASSOCIATIONS),
                propertySource.getTypedProperty(Boolean.class,
                        PropertyNames.CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE),
                propertySource.getTypedProperty(Boolean.class,
                        PropertyNames.CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST),
                propertySource.getTypedProperty(Long.class, PropertyNames.STAKING_REWARD_RATE),
                propertySource.getTypedProperty(Long.class, PropertyNames.STAKING_START_THRESH),
                propertySource.getTypedProperty(Integer.class, PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT),
                propertySource.getTypedProperty(Integer.class, PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_ALLOW_AUTO_ASSOCIATIONS),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.STAKING_IS_ENABLED),
                propertySource.getTypedProperty(Long.class,
                        PropertyNames.STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION),
                propertySource.getTypedProperty(Long.class, PropertyNames.ACCOUNTS_MAX_NUM),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_MAX_NUM),
                propertySource.getTypedProperty(Long.class, PropertyNames.FILES_MAX_NUM),
                propertySource.getTypedProperty(Long.class, PropertyNames.TOKENS_MAX_NUM),
                propertySource.getTypedProperty(Long.class, PropertyNames.TOKENS_MAX_AGGREGATE_RELS),
                propertySource.getTypedProperty(Long.class, PropertyNames.TOPICS_MAX_NUM),
                propertySource.getTypedProperty(Long.class, PropertyNames.SCHEDULING_MAX_NUM),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.UTIL_PRNG_IS_ENABLED),
                propertySource.getTypedProperty(Set.class, PropertyNames.CONTRACTS_SIDECARS),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_SIDECAR_VALIDATION_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.STAKING_REQUIRE_MIN_STAKE_TO_REWARD),
                propertySource.getTypedProperty(Integer.class, PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES),
                propertySource.getTypedProperty(Boolean.class,
                        PropertyNames.HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.TOKENS_AUTO_CREATIONS_ENABLED),
                propertySource.getTypedProperty(Boolean.class,
                        PropertyNames.HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.BALANCES_COMPRESS_ON_CREATION),
                propertySource.getTypedProperty(Long.class, PropertyNames.TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC),
                propertySource.getTypedProperty(Long.class,
                        PropertyNames.TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.LAZY_CREATION_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CRYPTO_CREATE_WITH_ALIAS_ENABLED),
                propertySource.getTypedProperty(Boolean.class, PropertyNames.CONTRACTS_ENFORCE_CREATION_THROTTLE),
                propertySource.getTypedProperty(Set.class, PropertyNames.CONTRACTS_PERMITTED_DELEGATE_CALLERS),
                propertySource.getTypedProperty(Long.class, PropertyNames.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS),
                propertySource.getTypedProperty(Set.class, PropertyNames.CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS));
    }

    private String exceptionMessagePropertyDoesNotExist(final String name) {
        return "Config property with name '" + name + "' does not exist!";
    }
}
