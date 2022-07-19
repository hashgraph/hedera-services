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
                    "bootstrap.feeSchedulesJson.resource",
                    "bootstrap.genesisPublicKey",
                    "bootstrap.hapiPermissions.path",
                    "bootstrap.networkProperties.path",
                    "bootstrap.rates.currentHbarEquiv",
                    "bootstrap.rates.currentCentEquiv",
                    "bootstrap.rates.currentExpiry",
                    "bootstrap.rates.nextHbarEquiv",
                    "bootstrap.rates.nextCentEquiv",
                    "bootstrap.rates.nextExpiry",
                    "bootstrap.system.entityExpiry",
                    "bootstrap.throttleDefsJson.resource");

    private static final Set<String> GLOBAL_STATIC_PROPS =
            Set.of(
                    "accounts.addressBookAdmin",
                    "accounts.exchangeRatesAdmin",
                    "accounts.feeSchedulesAdmin",
                    "accounts.freezeAdmin",
                    "accounts.nodeRewardAccount",
                    "accounts.stakingRewardAccount",
                    "accounts.systemAdmin",
                    "accounts.systemDeleteAdmin",
                    "accounts.systemUndeleteAdmin",
                    "accounts.treasury",
                    "entities.maxLifetime",
                    "entities.systemDeletable",
                    "files.addressBook",
                    "files.networkProperties",
                    "files.exchangeRates",
                    "files.feeSchedules",
                    "files.hapiPermissions",
                    "files.nodeDetails",
                    "files.softwareUpdateRange",
                    "files.throttleDefinitions",
                    "hedera.firstUserEntity",
                    "hedera.realm",
                    "hedera.shard",
                    "ledger.numSystemAccounts",
                    "ledger.totalTinyBarFloat",
                    "ledger.id",
                    "staking.periodMins",
                    "staking.rewardHistory.numStoredPeriods");

    static final Set<String> GLOBAL_DYNAMIC_PROPS =
            Set.of(
                    "autoCreation.enabled",
                    "balances.exportDir.path",
                    "balances.exportEnabled",
                    "balances.exportPeriodSecs",
                    "balances.exportTokenBalances",
                    "balances.nodeBalanceWarningThreshold",
                    "cache.records.ttl",
                    "contracts.defaultLifetime",
                    "contracts.knownBlockHash",
                    "contracts.localCall.estRetBytes",
                    "contracts.allowCreate2",
                    "contracts.allowAutoAssociations",
                    "contracts.maxGasPerSec",
                    "contracts.maxKvPairs.aggregate",
                    "contracts.maxKvPairs.individual",
                    "contracts.chainId",
                    "contracts.sidecars",
                    "contracts.throttle.throttleByGas",
                    "contracts.maxRefundPercentOfGasLimit",
                    "contracts.scheduleThrottleMaxGasLimit",
                    "contracts.redirectTokenCalls",
                    "contracts.precompile.exchangeRateGasCost",
                    "contracts.precompile.htsDefaultGasCost",
                    "contracts.precompile.exportRecordResults",
                    "contracts.precompile.htsEnableTokenCreate",
                    "files.maxSizeKb",
                    "fees.minCongestionPeriod",
                    "fees.percentCongestionMultipliers",
                    "fees.tokenTransferUsageMultiplier",
                    "hedera.transaction.maxMemoUtf8Bytes",
                    "hedera.transaction.maxValidDuration",
                    "hedera.transaction.minValidDuration",
                    "hedera.transaction.minValidityBufferSecs",
                    "hedera.recordStream.recordFileVersion",
                    "hedera.recordStream.signatureFileVersion",
                    "autoRemove.maxPurgedKvPairsPerTouch",
                    "autoRemove.maxReturnedNftsPerTouch",
                    "autoRenew.targetTypes",
                    "autorenew.numberOfEntitiesToScan",
                    "autorenew.maxNumberOfEntitiesToRenewOrDelete",
                    "autorenew.gracePeriod",
                    "ledger.changeHistorian.memorySecs",
                    "ledger.autoRenewPeriod.maxDuration",
                    "ledger.autoRenewPeriod.minDuration",
                    "ledger.xferBalanceChanges.maxLen",
                    "ledger.fundingAccount",
                    "ledger.maxAccountNum",
                    "ledger.transfers.maxLen",
                    "ledger.tokenTransfers.maxLen",
                    "ledger.nftTransfers.maxLen",
                    "ledger.records.maxQueryableByAccount",
                    "ledger.schedule.txExpiryTimeSecs",
                    "rates.intradayChangeLimitPercent",
                    "rates.midnightCheckInterval",
                    "scheduling.longTermEnabled",
                    "scheduling.maxTxnPerSecond",
                    "scheduling.maxExpirationFutureSeconds",
                    "scheduling.whitelist",
                    "sigs.expandFromLastSignedState",
                    "staking.fees.nodeRewardPercentage",
                    "staking.fees.stakingRewardPercentage",
                    "staking.isEnabled",
                    "staking.maxDailyStakeRewardThPerH",
                    "staking.rewardRate",
                    "staking.startThreshold",
                    "tokens.maxRelsPerInfoQuery",
                    "tokens.maxPerAccount",
                    "tokens.maxSymbolUtf8Bytes",
                    "tokens.maxTokenNameUtf8Bytes",
                    "tokens.maxCustomFeesAllowed",
                    "tokens.maxCustomFeeDepth",
                    "tokens.nfts.areEnabled",
                    "tokens.nfts.maxMetadataBytes",
                    "tokens.nfts.maxBatchSizeBurn",
                    "tokens.nfts.maxBatchSizeWipe",
                    "tokens.nfts.maxBatchSizeMint",
                    "tokens.nfts.maxAllowedMints",
                    "tokens.nfts.maxQueryRange",
                    "tokens.nfts.mintThrottleScaleFactor",
                    "tokens.nfts.useTreasuryWildcards",
                    "consensus.message.maxBytesAllowed",
                    "consensus.handle.maxPrecedingRecords",
                    "consensus.handle.maxFollowingRecords",
                    "upgrade.artifacts.path",
                    "hedera.allowances.maxTransactionLimit",
                    "hedera.allowances.maxAccountLimit",
                    "hedera.allowances.isEnabled",
                    "entities.limitTokenAssociations",
                    "prng.isEnabled");

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
                    "iss.dumpFcms",
                    "iss.resetPeriod",
                    "iss.roundsToDump",
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
                    "stats.executionTimesToTrack",
                    "stats.hapiOps.speedometerUpdateIntervalMs",
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
                    entry("accounts.addressBookAdmin", AS_LONG),
                    entry("accounts.exchangeRatesAdmin", AS_LONG),
                    entry("accounts.feeSchedulesAdmin", AS_LONG),
                    entry("accounts.freezeAdmin", AS_LONG),
                    entry("accounts.nodeRewardAccount", AS_LONG),
                    entry("accounts.stakingRewardAccount", AS_LONG),
                    entry("accounts.systemAdmin", AS_LONG),
                    entry("accounts.systemDeleteAdmin", AS_LONG),
                    entry("accounts.systemUndeleteAdmin", AS_LONG),
                    entry("accounts.treasury", AS_LONG),
                    entry("balances.exportEnabled", AS_BOOLEAN),
                    entry("balances.exportPeriodSecs", AS_INT),
                    entry("balances.nodeBalanceWarningThreshold", AS_LONG),
                    entry("cache.records.ttl", AS_INT),
                    entry("dev.onlyDefaultNodeListens", AS_BOOLEAN),
                    entry("balances.exportTokenBalances", AS_BOOLEAN),
                    entry("entities.maxLifetime", AS_LONG),
                    entry("entities.systemDeletable", AS_ENTITY_TYPES),
                    entry("files.addressBook", AS_LONG),
                    entry("files.networkProperties", AS_LONG),
                    entry("files.exchangeRates", AS_LONG),
                    entry("files.feeSchedules", AS_LONG),
                    entry("files.hapiPermissions", AS_LONG),
                    entry("files.nodeDetails", AS_LONG),
                    entry("files.softwareUpdateRange", AS_ENTITY_NUM_RANGE),
                    entry("files.throttleDefinitions", AS_LONG),
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
                    entry("hedera.recordStream.recordFileVersion", AS_INT),
                    entry("hedera.recordStream.signatureFileVersion", AS_INT),
                    entry("hedera.recordStream.queueCapacity", AS_INT),
                    entry("hedera.shard", AS_LONG),
                    entry("hedera.transaction.maxMemoUtf8Bytes", AS_INT),
                    entry("hedera.transaction.maxValidDuration", AS_LONG),
                    entry("hedera.transaction.minValidDuration", AS_LONG),
                    entry("hedera.transaction.minValidityBufferSecs", AS_INT),
                    entry("autoCreation.enabled", AS_BOOLEAN),
                    entry("autoRemove.maxPurgedKvPairsPerTouch", AS_INT),
                    entry("autoRemove.maxReturnedNftsPerTouch", AS_INT),
                    entry("autoRenew.targetTypes", AS_ENTITY_TYPES),
                    entry("autorenew.numberOfEntitiesToScan", AS_INT),
                    entry("autorenew.maxNumberOfEntitiesToRenewOrDelete", AS_INT),
                    entry("autorenew.gracePeriod", AS_LONG),
                    entry("ledger.autoRenewPeriod.maxDuration", AS_LONG),
                    entry("ledger.autoRenewPeriod.minDuration", AS_LONG),
                    entry("netty.mode", AS_PROFILE),
                    entry("queries.blob.lookupRetries", AS_INT),
                    entry("netty.startRetries", AS_INT),
                    entry("netty.startRetryIntervalMs", AS_LONG),
                    entry("bootstrap.rates.currentHbarEquiv", AS_INT),
                    entry("bootstrap.rates.currentCentEquiv", AS_INT),
                    entry("bootstrap.rates.currentExpiry", AS_LONG),
                    entry("bootstrap.rates.nextHbarEquiv", AS_INT),
                    entry("bootstrap.rates.nextCentEquiv", AS_INT),
                    entry("bootstrap.rates.nextExpiry", AS_LONG),
                    entry("bootstrap.system.entityExpiry", AS_LONG),
                    entry("fees.minCongestionPeriod", AS_INT),
                    entry("fees.tokenTransferUsageMultiplier", AS_INT),
                    entry("fees.percentCongestionMultipliers", AS_CONGESTION_MULTIPLIERS),
                    entry("files.maxSizeKb", AS_INT),
                    entry("ledger.changeHistorian.memorySecs", AS_INT),
                    entry("ledger.xferBalanceChanges.maxLen", AS_INT),
                    entry("ledger.fundingAccount", AS_LONG),
                    entry("ledger.maxAccountNum", AS_LONG),
                    entry("ledger.numSystemAccounts", AS_INT),
                    entry("ledger.transfers.maxLen", AS_INT),
                    entry("ledger.tokenTransfers.maxLen", AS_INT),
                    entry("ledger.nftTransfers.maxLen", AS_INT),
                    entry("ledger.totalTinyBarFloat", AS_LONG),
                    entry("ledger.schedule.txExpiryTimeSecs", AS_INT),
                    entry("ledger.records.maxQueryableByAccount", AS_INT),
                    entry("iss.dumpFcms", AS_BOOLEAN),
                    entry("iss.resetPeriod", AS_INT),
                    entry("iss.roundsToDump", AS_INT),
                    entry("netty.prod.flowControlWindow", AS_INT),
                    entry("netty.prod.maxConcurrentCalls", AS_INT),
                    entry("netty.prod.maxConnectionAge", AS_LONG),
                    entry("netty.prod.maxConnectionAgeGrace", AS_LONG),
                    entry("netty.prod.maxConnectionIdle", AS_LONG),
                    entry("netty.prod.keepAliveTime", AS_LONG),
                    entry("netty.prod.keepAliveTimeout", AS_LONG),
                    entry("staking.fees.nodeRewardPercentage", AS_INT),
                    entry("staking.fees.stakingRewardPercentage", AS_INT),
                    entry("staking.periodMins", AS_LONG),
                    entry("staking.rewardHistory.numStoredPeriods", AS_INT),
                    entry("staking.rewardRate", AS_LONG),
                    entry("staking.startThreshold", AS_LONG),
                    entry("tokens.maxRelsPerInfoQuery", AS_INT),
                    entry("tokens.maxPerAccount", AS_INT),
                    entry("tokens.maxCustomFeesAllowed", AS_INT),
                    entry("tokens.maxCustomFeeDepth", AS_INT),
                    entry("tokens.maxSymbolUtf8Bytes", AS_INT),
                    entry("tokens.maxTokenNameUtf8Bytes", AS_INT),
                    entry("tokens.nfts.maxMetadataBytes", AS_INT),
                    entry("tokens.nfts.maxBatchSizeBurn", AS_INT),
                    entry("tokens.nfts.mintThrottleScaleFactor", AS_THROTTLE_SCALE_FACTOR),
                    entry("tokens.nfts.maxBatchSizeWipe", AS_INT),
                    entry("tokens.nfts.maxBatchSizeMint", AS_INT),
                    entry("tokens.nfts.maxAllowedMints", AS_LONG),
                    entry("tokens.nfts.maxQueryRange", AS_LONG),
                    entry("tokens.nfts.useTreasuryWildcards", AS_BOOLEAN),
                    entry("contracts.knownBlockHash", AS_KNOWN_BLOCK_VALUES),
                    entry("contracts.localCall.estRetBytes", AS_INT),
                    entry("contracts.allowCreate2", AS_BOOLEAN),
                    entry("contracts.allowAutoAssociations", AS_BOOLEAN),
                    entry("contracts.defaultLifetime", AS_LONG),
                    entry("contracts.maxGasPerSec", AS_LONG),
                    entry("contracts.maxKvPairs.aggregate", AS_LONG),
                    entry("contracts.maxKvPairs.individual", AS_INT),
                    entry("contracts.chainId", AS_INT),
                    entry("contracts.maxRefundPercentOfGasLimit", AS_INT),
                    entry("contracts.scheduleThrottleMaxGasLimit", AS_LONG),
                    entry("contracts.redirectTokenCalls", AS_BOOLEAN),
                    entry("contracts.precompile.exchangeRateGasCost", AS_LONG),
                    entry("contracts.precompile.htsDefaultGasCost", AS_LONG),
                    entry("contracts.precompile.exportRecordResults", AS_BOOLEAN),
                    entry("contracts.precompile.htsEnableTokenCreate", AS_BOOLEAN),
                    entry("contracts.throttle.throttleByGas", AS_BOOLEAN),
                    entry("contracts.sidecars", AS_SIDECARS),
                    entry("rates.intradayChangeLimitPercent", AS_INT),
                    entry("rates.midnightCheckInterval", AS_LONG),
                    entry("sigs.expandFromLastSignedState", AS_BOOLEAN),
                    entry("scheduling.longTermEnabled", AS_BOOLEAN),
                    entry("scheduling.maxTxnPerSecond", AS_LONG),
                    entry("scheduling.maxExpirationFutureSeconds", AS_LONG),
                    entry("scheduling.whitelist", AS_FUNCTIONS),
                    entry("staking.isEnabled", AS_BOOLEAN),
                    entry("staking.maxDailyStakeRewardThPerH", AS_LONG),
                    entry("stats.hapiOps.speedometerUpdateIntervalMs", AS_LONG),
                    entry("stats.runningAvgHalfLifeSecs", AS_DOUBLE),
                    entry("stats.speedometerHalfLifeSecs", AS_DOUBLE),
                    entry("consensus.message.maxBytesAllowed", AS_INT),
                    entry("consensus.handle.maxPrecedingRecords", AS_LONG),
                    entry("consensus.handle.maxFollowingRecords", AS_LONG),
                    entry("tokens.nfts.areEnabled", AS_BOOLEAN),
                    entry("stats.executionTimesToTrack", AS_INT),
                    entry("hedera.allowances.maxTransactionLimit", AS_INT),
                    entry("hedera.allowances.maxAccountLimit", AS_INT),
                    entry("hedera.allowances.isEnabled", AS_BOOLEAN),
                    entry("entities.limitTokenAssociations", AS_BOOLEAN),
                    entry("prng.isEnabled", AS_BOOLEAN));
}
