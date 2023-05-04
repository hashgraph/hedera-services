/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.config.VersionedConfiguration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigProviderImplTest {

    @Mock(strictness = Strictness.LENIENT)
    private PropertySource propertySource;

    @BeforeEach
    void configureMockForConfigData() {
        final Set<String> stringProperties = Set.of(
                "pathToBalancesExportDir",
                "evmVersion",
                "upgradeArtifactsLoc",
                "recordLogDir",
                "nettyTlsCrtPath",
                "nettyTlsKeyPath",
                "devListeningAccount",
                "accountsExportPath",
                "consThrottlesToSample",
                "hapiThrottlesToSample",
                "sidecarDir");
        final Set<String> booleanProperties = Set.of(
                "allowTreasuryToOwnNfts",
                "shouldExportBalances",
                "shouldExportTokenBalances",
                "dynamicEvmVersion",
                "schedulingLongTermEnabled",
                "areNftsEnabled",
                "throttleByGas",
                "autoCreationEnabled",
                "expandSigsFromImmutableState",
                "exportPrecompileResults",
                "create2Enabled",
                "redirectTokenCalls",
                "enableAllowances",
                "limitTokenAssociations",
                "enableHTSPrecompileCreate",
                "atomicCryptoTransferEnabled",
                "contractAutoAssociationsEnabled",
                "stakingEnabled",
                "utilPrngEnabled",
                "sidecarValidationEnabled",
                "requireMinStakeToReward",
                "itemizeStorageFees",
                "compressRecordFilesOnCreation",
                "tokenAutoCreationsEnabled",
                "doTraceabilityExport",
                "compressAccountBalanceFilesOnCreation",
                "lazyCreationEnabled",
                "cryptoCreateWithAliasEnabled",
                "enforceContractCreationThrottle",
                "recordStreamEnabled",
                "devOnlyDefaultNodeListens",
                "exportAccountsOnStartup");
        final Set<String> numericProperties = Set.of(
                "maxNftMetadataBytes",
                "maxBatchSizeBurn",
                "maxBatchSizeMint",
                "maxNftTransfersLen",
                "maxBatchSizeWipe",
                "maxNftQueryRange",
                "maxTokensPerAccount",
                "maxTokenRelsPerInfoQuery",
                "maxCustomFeesAllowed",
                "maxTokenSymbolUtf8Bytes",
                "maxTokenNameUtf8Bytes",
                "maxFileSizeKb",
                "cacheRecordsTtl",
                "balancesExportPeriodSecs",
                "ratesIntradayChangeLimitPercent",
                "nodeBalanceWarningThreshold",
                "maxTransfersLen",
                "maxTokenTransfersLen",
                "maxMemoUtf8Bytes",
                "maxTxnDuration",
                "minTxnDuration",
                "minValidityBuffer",
                "maxGasPerSec",
                "defaultContractLifetime",
                "feesTokenTransferUsageMultiplier",
                "autoRenewNumberOfEntitiesToScan",
                "autoRenewMaxNumberOfEntitiesToRenewOrDelete",
                "autoRenewGracePeriod",
                "maxAutoRenewDuration",
                "minAutoRenewDuration",
                "localCallEstRetBytes",
                "schedulingMaxTxnPerSecond",
                "schedulingMaxExpirationFutureSeconds",
                "scheduledTxExpiryTimeSecs",
                "messageMaxBytesAllowed",
                "maxPrecedingRecords",
                "maxFollowingRecords",
                "feesMinCongestionPeriod",
                "maxNftMints",
                "maxXferBalanceChanges",
                "maxCustomFeeDepth",
                "contractMaxRefundPercentOfGasLimit",
                "scheduleThrottleMaxGasLimit",
                "htsDefaultGasCost",
                "changeHistorianMemorySecs",
                "maxAggregateContractKvPairs",
                "maxIndividualContractKvPairs",
                "maxMostRecentQueryableRecords",
                "maxAllowanceLimitPerTransaction",
                "maxAllowanceLimitPerAccount",
                "exchangeRateGasReq",
                "stakingRewardRate",
                "stakingStartThreshold",
                "nodeRewardPercent",
                "stakingRewardPercent",
                "maxDailyStakeRewardThPerH",
                "recordFileVersion",
                "recordSignatureFileVersion",
                "maxNumAccounts",
                "maxNumContracts",
                "maxNumFiles",
                "maxNumTokens",
                "maxNumTokenRels",
                "maxNumTopics",
                "maxNumSchedules",
                "sidecarMaxSizeMb",
                "traceabilityMaxExportsPerConsSec",
                "traceabilityMinFreeToUsedGasThrottleRatio",
                "maxNumWithHapiSigsAccess",
                "port",
                "tlsPort",
                "hapiOpStatsUpdateIntervalMs",
                "entityUtilStatsUpdateIntervalMs",
                "throttleUtilStatsUpdateIntervalMs",
                "statsSpeedometerHalfLifeSecs",
                "statsRunningAvgHalfLifeSecs",
                "recordLogPeriod",
                "recordStreamQueueCapacity",
                "queryBlobLookupRetries",
                "nettyProdKeepAliveTime",
                "nettyMaxConnectionAge",
                "nettyMaxConnectionAgeGrace",
                "nettyMaxConnectionIdle",
                "nettyMaxConcurrentCalls",
                "nettyFlowControlWindow",
                "nettyStartRetries",
                "nettyStartRetryIntervalMs",
                "numExecutionTimesToTrack",
                "issResetPeriod",
                "issRoundsToLog",
                "prefetchQueueCapacity",
                "prefetchThreadPoolSize",
                "prefetchCodeCacheTtlSecs",
                "workflowsPort",
                "workflowsTlsPort",
                "nettyProdKeepAliveTimeout");
        final Set<String> customProperties = Set.of("fundingAccount", "activeProfile", "nettyMode");
        final Set<String> allProperties = new HashSet<>();
        allProperties.addAll(stringProperties);
        allProperties.addAll(booleanProperties);
        allProperties.addAll(numericProperties);
        allProperties.addAll(customProperties);
        when(propertySource.allPropertyNames()).thenReturn(allProperties);

        stringProperties.forEach(
                property -> when(propertySource.getRawValue(property)).thenReturn("test"));
        booleanProperties.forEach(
                property -> when(propertySource.getRawValue(property)).thenReturn("true"));
        numericProperties.forEach(
                property -> when(propertySource.getRawValue(property)).thenReturn("1"));
    }

    @Test
    void testInvalidCreation() {
        assertThatThrownBy(() -> new ConfigProviderImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialConfig() {
        // given
        final var configProvider = new ConfigProviderImpl(propertySource);

        // when
        final var configuration = configProvider.getConfiguration();

        // then
        assertThat(configuration).isNotNull();
        assertThat(configuration.getVersion()).isZero();
    }

    @Test
    void testUpdateCreatesNewConfig() {
        // given
        final var configProvider = new ConfigProviderImpl(propertySource);

        // when
        final var configuration1 = configProvider.getConfiguration();
        configProvider.update("name", "value");
        final var configuration2 = configProvider.getConfiguration();

        // then
        assertThat(configuration1).isNotSameAs(configuration2);
        assertThat(configuration1).returns(0L, VersionedConfiguration::getVersion);
        assertThat(configuration2).returns(1L, VersionedConfiguration::getVersion);
    }

    @Test
    void testUpdatedValue() {
        // given
        final var configProvider = new ConfigProviderImpl(propertySource);
        final var configuration1 = configProvider.getConfiguration();
        final String value1 = configuration1.getValue("port");

        // when
        configProvider.update("port", "8080");
        final var configuration2 = configProvider.getConfiguration();
        final String value2 = configuration2.getValue("port");

        // then
        assertThat(value1).isNotSameAs(value2);
        assertThat(value1).isEqualTo("1");
        assertThat(value2).isEqualTo("8080");
    }
}
