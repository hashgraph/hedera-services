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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.fees.charging.ContractStoragePriceTiers;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalDynamicPropertiesTest {
    private static final String[] balanceExportPaths =
            new String[] {"/opt/hgcapp/accountBalances", "data/saved/accountBalances"};

    private static final String[] upgradeArtifactLocs =
            new String[] {"/opt/hgcapp/HapiApp2.0/data/upgrade", "data/upgrade"};

    private static final String[] evmVersions = new String[] {"vEven", "vOdd"};

    private static final String literalBlockValues =
            "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666";
    private static final KnownBlockValues blockValues = KnownBlockValues.from(literalBlockValues);

    private PropertySource properties;

    private HederaNumbers numbers;
    private CongestionMultipliers oddCongestion =
            CongestionMultipliers.from("90,11x,95,27x,99,103x");
    private CongestionMultipliers evenCongestion =
            CongestionMultipliers.from("90,10x,95,25x,99,100x");
    private ThrottleReqOpsScaleFactor oddFactor = ThrottleReqOpsScaleFactor.from("5:2");
    private ThrottleReqOpsScaleFactor evenFactor = ThrottleReqOpsScaleFactor.from("7:2");
    private GlobalDynamicProperties subject;

    @BeforeEach
    void setup() {
        numbers = mock(HederaNumbers.class);
        given(numbers.shard()).willReturn(1L);
        given(numbers.realm()).willReturn(2L);
        properties = mock(PropertySource.class);
    }

    @Test
    @SuppressWarnings("java:S5961")
    void constructsFlagsAsExpected() {
        givenPropsWithSeed(1);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertTrue(subject.shouldExportBalances());
        assertTrue(subject.shouldExportTokenBalances());
        assertTrue(subject.shouldAutoRenewSomeEntityType());
        assertTrue(subject.areNftsEnabled());
        assertTrue(subject.shouldThrottleByGas());
        assertFalse(subject.isAutoCreationEnabled());
        assertFalse(subject.expandSigsFromImmutableState());
        assertTrue(subject.shouldExportPrecompileResults());
        assertFalse(subject.isCreate2Enabled());
        assertTrue(subject.isRedirectTokenCallsEnabled());
        assertFalse(subject.areAllowancesEnabled());
        assertFalse(subject.areTokenAssociationsLimited());
        assertTrue(subject.isHTSPrecompileCreateEnabled());
        assertTrue(subject.areContractAutoAssociationsEnabled());
        assertTrue(subject.isStakingEnabled());
        assertFalse(subject.isUtilPrngEnabled());
        assertTrue(subject.requireMinStakeToReward());
        assertFalse(subject.shouldCompressRecordFilesOnCreation());
        assertTrue(subject.areTokenAutoCreationsEnabled());
        assertFalse(subject.dynamicEvmVersion());
        assertFalse(subject.shouldCompressAccountBalanceFilesOnCreation());
        assertTrue(subject.shouldDoTraceabilityExport());
    }

    @Test
    void nftPropertiesTest() {
        givenPropsWithSeed(1);
        subject = new GlobalDynamicProperties(numbers, properties);

        assertEquals(37, subject.maxNftTransfersLen());
        assertEquals(38, subject.maxBatchSizeBurn());
        assertEquals(39, subject.maxBatchSizeWipe());
        assertEquals(40, subject.maxBatchSizeMint());
        assertEquals(41, subject.maxNftQueryRange());
        assertFalse(subject.treasuryNftAllowance());
        assertEquals(42, subject.maxNftMetadataBytes());
        assertEquals(43, subject.maxTokenNameUtf8Bytes());
        assertEquals(oddFactor, subject.nftMintScaleFactor());
    }

    @Test
    void constructsNonMaxIntsAsExpected() {
        givenPropsWithSeed(1);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertEquals(8, subject.cacheRecordsTtl());
        assertEquals(10, subject.ratesIntradayChangeLimitPercent());
        assertEquals(11, subject.balancesExportPeriodSecs());
        assertEquals(20, subject.minValidityBuffer());
        final var chainIdBytes = Integers.toBytes(22);
        assertArrayEquals(chainIdBytes, subject.chainIdBytes());
        assertEquals(Bytes32.leftPad(Bytes.of(chainIdBytes)), subject.chainIdBytes32());
        assertEquals(24, subject.feesTokenTransferUsageMultiplier());
        assertEquals(26, subject.minAutoRenewDuration());
        assertEquals(26, subject.typedMinAutoRenewDuration().getSeconds());
        assertEquals(27, subject.localCallEstRetBytes());
        assertEquals(28, subject.scheduledTxExpiryTimeSecs());
        assertEquals(29, subject.messageMaxBytesAllowed());
        assertEquals(30, subject.feesMinCongestionPeriod());
        assertEquals(32, subject.autoRenewNumberOfEntitiesToScan());
        assertEquals(33, subject.autoRenewMaxNumberOfEntitiesToRenewOrDelete());
        assertEquals(78, subject.recordFileVersion());
        assertEquals(79, subject.recordSignatureFileVersion());
    }

    @Test
    void constructsMaxIntsAsExpected() {
        givenPropsWithSeed(1);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertEquals(1, subject.maxTokensRelsPerInfoQuery());
        assertEquals(1, subject.maxTokensPerAccount());
        assertEquals(2, subject.maxTokenSymbolUtf8Bytes());
        assertEquals(6, subject.maxFileSizeKb());
        assertEquals(15, subject.maxTransferListSize());
        assertEquals(16, subject.maxTokenTransferListSize());
        assertEquals(17, subject.maxMemoUtf8Bytes());
        assertEquals(21, subject.maxGasPerSec());
        assertEquals(25, subject.maxAutoRenewDuration());
        assertEquals(36, subject.maxCustomFeesAllowed());
        assertEquals(46, subject.maxXferBalanceChanges());
        assertEquals(47, subject.maxCustomFeeDepth());
        assertEquals(48, subject.maxGasRefundPercentage());
        assertEquals(52, subject.changeHistorianMemorySecs());
        assertEquals(53, subject.maxAggregateContractKvPairs());
        assertEquals(54, subject.maxIndividualContractKvPairs());
        assertEquals(55, subject.maxNumQueryableRecords());
        assertEquals(86, subject.maxNumTokenRels());
        assertEquals(89, subject.getSidecarMaxSizeMb());
    }

    @Test
    void constructsLongsAsExpected() {
        givenPropsWithSeed(1);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertEquals(13L, subject.nodeBalanceWarningThreshold());
        assertEquals(18L, subject.maxTxnDuration());
        assertEquals(19L, subject.minTxnDuration());
        assertEquals(23L, subject.defaultContractLifetime());
        assertEquals(34L, subject.autoRenewGracePeriod());
        assertEquals(44L, subject.maxNftMints());
        assertEquals(66L, subject.schedulingMaxTxnPerSecond());
        assertEquals(67L, subject.scheduleThrottleMaxGasLimit());
        assertEquals(68L, subject.schedulingMaxExpirationFutureSeconds());
        assertEquals(69L, subject.maxPrecedingRecords());
        assertEquals(70L, subject.maxFollowingRecords());
        assertEquals(76L, subject.maxDailyStakeRewardThPerH());
    }

    @Test
    void constructsMiscAsExpected() {
        givenPropsWithSeed(1);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // expect:
        assertEquals(accountWith(1L, 2L, 7L), subject.fundingAccount());
        assertEquals(balanceExportPaths[1], subject.pathToBalancesExportDir());
        assertEquals(Set.of(HederaFunctionality.CryptoTransfer), subject.schedulingWhitelist());
        assertEquals(oddCongestion, subject.congestionMultipliers());
        assertEquals(upgradeArtifactLocs[1], subject.upgradeArtifactsLoc());
        assertEquals(Set.of(SidecarType.CONTRACT_STATE_CHANGE), subject.enabledSidecars());
        assertEquals(Map.of(0L, 4L, 1L, 8L), subject.nodeMaxMinStakeRatios());
        assertEquals(
                ContractStoragePriceTiers.from("0til100M,2000til450M", 88, 53L, 87L),
                subject.storagePriceTiers());
        assertEquals(evmVersions[1], subject.evmVersion());
    }

    @Test
    @SuppressWarnings("java:S5961")
    void reloadsFlagsAsExpected() {
        givenPropsWithSeed(2);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertFalse(subject.shouldExportBalances());
        assertFalse(subject.shouldExportTokenBalances());
        assertTrue(subject.shouldAutoRenewSomeEntityType());
        assertFalse(subject.areNftsEnabled());
        assertFalse(subject.shouldThrottleByGas());
        assertTrue(subject.isAutoCreationEnabled());
        assertTrue(subject.expandSigsFromImmutableState());
        assertFalse(subject.shouldExportPrecompileResults());
        assertTrue(subject.isCreate2Enabled());
        assertFalse(subject.isRedirectTokenCallsEnabled());
        assertTrue(subject.areAllowancesEnabled());
        assertTrue(subject.shouldAutoRenewAccounts());
        assertTrue(subject.shouldAutoRenewContracts());
        assertTrue(subject.shouldAutoRenewSomeEntityType());
        assertTrue(subject.areTokenAssociationsLimited());
        assertFalse(subject.isHTSPrecompileCreateEnabled());
        assertTrue(subject.schedulingLongTermEnabled());
        assertFalse(subject.areContractAutoAssociationsEnabled());
        assertFalse(subject.isStakingEnabled());
        assertTrue(subject.isUtilPrngEnabled());
        assertTrue(subject.shouldItemizeStorageFees());
        assertTrue(subject.shouldCompressRecordFilesOnCreation());
        assertFalse(subject.areTokenAutoCreationsEnabled());
        assertTrue(subject.dynamicEvmVersion());
        assertTrue(subject.shouldCompressAccountBalanceFilesOnCreation());
    }

    @Test
    void knowsWhenNotToDoAnyAutoRenew() {
        givenPropsWithSeed(3);

        subject = new GlobalDynamicProperties(numbers, properties);

        assertFalse(subject.shouldAutoRenewSomeEntityType());
    }

    @Test
    @SuppressWarnings("java:S5961")
    void reloadsIntsAsExpected() {
        givenPropsWithSeed(2);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertEquals(2, subject.maxTokensRelsPerInfoQuery());
        assertEquals(2, subject.maxTokensPerAccount());
        assertEquals(3, subject.maxTokenSymbolUtf8Bytes());
        assertEquals(7, subject.maxFileSizeKb());
        assertEquals(9, subject.cacheRecordsTtl());
        assertEquals(11, subject.ratesIntradayChangeLimitPercent());
        assertEquals(12, subject.balancesExportPeriodSecs());
        assertEquals(16, subject.maxTransferListSize());
        assertEquals(17, subject.maxTokenTransferListSize());
        assertEquals(18, subject.maxMemoUtf8Bytes());
        assertEquals(21, subject.minValidityBuffer());
        assertEquals(22, subject.maxGasPerSec());
        assertEquals(25, subject.feesTokenTransferUsageMultiplier());
        assertEquals(26, subject.maxAutoRenewDuration());
        assertEquals(27, subject.minAutoRenewDuration());
        assertEquals(28, subject.localCallEstRetBytes());
        assertEquals(29, subject.scheduledTxExpiryTimeSecs());
        assertEquals(30, subject.messageMaxBytesAllowed());
        assertEquals(31, subject.feesMinCongestionPeriod());
        assertEquals(33, subject.autoRenewNumberOfEntitiesToScan());
        assertEquals(34, subject.autoRenewMaxNumberOfEntitiesToRenewOrDelete());
        assertEquals(37, subject.maxCustomFeesAllowed());
        assertEquals(47, subject.maxXferBalanceChanges());
        assertEquals(48, subject.maxCustomFeeDepth());
        assertEquals(49, subject.maxGasRefundPercentage());
        assertEquals(53, subject.changeHistorianMemorySecs());
        assertEquals(54, subject.maxAggregateContractKvPairs());
        assertEquals(55, subject.maxIndividualContractKvPairs());
        assertEquals(57, subject.maxAllowanceLimitPerTransaction());
        assertEquals(58, subject.maxAllowanceLimitPerAccount());
        assertEquals(73, subject.getNodeRewardPercent());
        assertEquals(74, subject.getStakingRewardPercent());
        assertEquals(79, subject.recordFileVersion());
        assertEquals(80, subject.recordSignatureFileVersion());
        assertEquals(90, subject.getSidecarMaxSizeMb());
    }

    @Test
    void reloadsLongsAsExpected() {
        givenPropsWithSeed(2);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // then:
        assertEquals(14L, subject.nodeBalanceWarningThreshold());
        assertEquals(19L, subject.maxTxnDuration());
        assertEquals(20L, subject.minTxnDuration());
        assertEquals(24L, subject.defaultContractLifetime());
        assertEquals(35L, subject.autoRenewGracePeriod());
        assertEquals(45L, subject.maxNftMints());
        assertEquals(54L, subject.htsDefaultGasCost());
        assertEquals(72L, subject.getStakingStartThreshold());
        assertEquals(67L, subject.schedulingMaxTxnPerSecond());
        assertEquals(68L, subject.scheduleThrottleMaxGasLimit());
        assertEquals(69L, subject.schedulingMaxExpirationFutureSeconds());
        assertEquals(76L, subject.getStakingRewardRate());
        assertEquals(70L, subject.maxPrecedingRecords());
        assertEquals(71L, subject.maxFollowingRecords());
        assertEquals(76L, subject.getStakingRewardRate());
        assertEquals(77L, subject.maxDailyStakeRewardThPerH());
        assertEquals(81L, subject.maxNumAccounts());
        assertEquals(82L, subject.maxNumContracts());
        assertEquals(83L, subject.maxNumFiles());
        assertEquals(84L, subject.maxNumTokens());
        assertEquals(85L, subject.maxNumTopics());
        assertEquals(86L, subject.maxNumSchedules());
    }

    @Test
    void reloadsMiscAsExpected() {
        givenPropsWithSeed(2);

        // when:
        subject = new GlobalDynamicProperties(numbers, properties);

        // expect:
        assertEquals(accountWith(1L, 2L, 8L), subject.fundingAccount());
        assertEquals(balanceExportPaths[0], subject.pathToBalancesExportDir());
        assertEquals(Set.of(HederaFunctionality.CryptoCreate), subject.schedulingWhitelist());
        assertEquals(evenCongestion, subject.congestionMultipliers());
        assertEquals(evenFactor, subject.nftMintScaleFactor());
        assertEquals(upgradeArtifactLocs[0], subject.upgradeArtifactsLoc());
        assertEquals(blockValues, subject.knownBlockValues());
        assertEquals(66L, subject.exchangeRateGasReq());
        assertEquals(Set.of(SidecarType.CONTRACT_BYTECODE), subject.enabledSidecars());
        assertEquals(evmVersions[0], subject.evmVersion());
    }

    private void givenPropsWithSeed(int i) {
        given(properties.getIntProperty(TOKENS_MAX_RELS_PER_INFO_QUERY)).willReturn(i);
        given(properties.getIntProperty(TOKENS_MAX_PER_ACCOUNT)).willReturn(i);
        given(properties.getIntProperty(TOKENS_MAX_SYMBOL_UTF8_BYTES)).willReturn(i + 1);
        given(properties.getBooleanProperty("ledger.keepRecordsInState")).willReturn((i % 2) == 0);
        given(properties.getIntProperty(FILES_MAX_SIZE_KB)).willReturn(i + 5);
        given(properties.getLongProperty(LEDGER_FUNDING_ACCOUNT)).willReturn((long) i + 6);
        given(properties.getIntProperty(CACHE_RECORDS_TTL)).willReturn(i + 7);
        given(properties.getIntProperty(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT)).willReturn(i + 9);
        given(properties.getIntProperty(BALANCES_EXPORT_PERIOD_SECS)).willReturn(i + 10);
        given(properties.getBooleanProperty(BALANCES_EXPORT_ENABLED)).willReturn((i + 11) % 2 == 0);
        given(properties.getLongProperty(BALANCES_NODE_BALANCE_WARN_THRESHOLD)).willReturn(i + 12L);
        given(properties.getStringProperty(BALANCES_EXPORT_DIR_PATH))
                .willReturn(balanceExportPaths[i % 2]);
        given(properties.getBooleanProperty(BALANCES_EXPORT_TOKEN_BALANCES))
                .willReturn((i + 13) % 2 == 0);
        given(properties.getIntProperty(LEDGER_TRANSFERS_MAX_LEN)).willReturn(i + 14);
        given(properties.getIntProperty(LEDGER_TOKEN_TRANSFERS_MAX_LEN)).willReturn(i + 15);
        given(properties.getIntProperty(HEDERA_TXN_MAX_MEMO_UTF8_BYTES)).willReturn(i + 16);
        given(properties.getLongProperty(HEDERA_TXN_MAX_VALID_DURATION)).willReturn(i + 17L);
        given(properties.getLongProperty(HEDERA_TXN_MIN_VALID_DURATION)).willReturn(i + 18L);
        given(properties.getIntProperty(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS)).willReturn(i + 19);
        given(properties.getLongProperty(CONTRACTS_MAX_GAS_PER_SEC)).willReturn(i + 20L);
        given(properties.getIntProperty(CONTRACTS_CHAIN_ID)).willReturn(i + 21);
        given(properties.getLongProperty(CONTRACTS_DEFAULT_LIFETIME)).willReturn(i + 22L);
        given(properties.getIntProperty(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER)).willReturn(i + 23);
        given(properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION))
                .willReturn(i + 24L);
        given(properties.getLongProperty(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION))
                .willReturn(i + 25L);
        given(properties.getIntProperty(CONTRACTS_LOCAL_CALL_EST_RET_BYTES)).willReturn(i + 26);
        given(properties.getIntProperty(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS)).willReturn(i + 27);
        given(properties.getIntProperty(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED)).willReturn(i + 28);
        given(properties.getBooleanProperty(SCHEDULING_LONG_TERM_ENABLED)).willReturn(i % 2 == 0);
        given(properties.getFunctionsProperty(SCHEDULING_WHITE_LIST))
                .willReturn(
                        i % 2 == 0
                                ? Set.of(HederaFunctionality.CryptoCreate)
                                : Set.of(HederaFunctionality.CryptoTransfer));
        given(properties.getCongestionMultiplierProperty(FEES_PERCENT_CONGESTION_MULTIPLIERS))
                .willReturn(i % 2 == 0 ? evenCongestion : oddCongestion);
        given(properties.getIntProperty(FEES_MIN_CONGESTION_PERIOD)).willReturn(i + 29);
        given(properties.getIntProperty(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN)).willReturn(i + 31);
        given(properties.getIntProperty(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE))
                .willReturn(i + 32);
        given(properties.getLongProperty(AUTO_RENEW_GRACE_PERIOD)).willReturn(i + 33L);
        given(properties.getIntProperty(TOKENS_MAX_CUSTOM_FEES_ALLOWED)).willReturn(i + 35);
        given(properties.getIntProperty(LEDGER_NFT_TRANSFERS_MAX_LEN)).willReturn(i + 36);
        given(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_BURN)).willReturn(i + 37);
        given(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE)).willReturn(i + 38);
        given(properties.getIntProperty(TOKENS_NFTS_MAX_BATCH_SIZE_MINT)).willReturn(i + 39);
        given(properties.getLongProperty(TOKENS_NFTS_MAX_QUERY_RANGE)).willReturn(i + 40L);
        given(properties.getIntProperty(TOKENS_NFTS_MAX_METADATA_BYTES)).willReturn(i + 41);
        given(properties.getIntProperty(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES)).willReturn(i + 42);
        given(properties.getBooleanProperty(TOKENS_NFTS_ARE_ENABLED)).willReturn((i + 43) % 2 == 0);
        given(properties.getLongProperty(TOKENS_NFTS_MAX_ALLOWED_MINTS)).willReturn(i + 43L);
        given(properties.getIntProperty(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR)).willReturn(i + 44);
        given(properties.getIntProperty(LEDGER_XFER_BAL_CHANGES_MAX_LEN)).willReturn(i + 45);
        given(properties.getIntProperty(TOKENS_MAX_CUSTOM_FEE_DEPTH)).willReturn(i + 46);
        given(properties.getThrottleScaleFactor(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR))
                .willReturn(i % 2 == 0 ? evenFactor : oddFactor);
        given(properties.getStringProperty(UPGRADE_ARTIFACTS_PATH))
                .willReturn(upgradeArtifactLocs[i % 2]);
        given(properties.getBooleanProperty(CONTRACTS_THROTTLE_THROTTLE_BY_GAS))
                .willReturn((i + 47) % 2 == 0);
        given(properties.getIntProperty(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT))
                .willReturn(i + 47);
        given(properties.getIntProperty(LEDGER_CHANGE_HIST_MEM_SECS)).willReturn(i + 51);
        given(properties.getLongProperty(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST))
                .willReturn(i + 52L);
        given(properties.getBooleanProperty(AUTO_CREATION_ENABLED)).willReturn(i % 2 == 0);
        given(properties.getBooleanProperty(SIGS_EXPAND_FROM_IMMUTABLE_STATE))
                .willReturn(i % 2 == 0);
        given(properties.getLongProperty(CONTRACTS_MAX_KV_PAIRS_AGGREGATE)).willReturn(i + 52L);
        given(properties.getIntProperty(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL)).willReturn(i + 53);
        given(properties.getIntProperty(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT))
                .willReturn(i + 54);
        given(properties.getIntProperty(HEDERA_ALLOWANCES_MAX_TXN_LIMIT)).willReturn(i + 55);
        given(properties.getIntProperty(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT)).willReturn(i + 56);
        given(properties.getBooleanProperty(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS))
                .willReturn((i + 57) % 2 == 0);
        given(properties.getBooleanProperty(CONTRACTS_ALLOW_CREATE2)).willReturn((i + 58) % 2 == 0);
        given(properties.getBooleanProperty(CONTRACTS_REDIRECT_TOKEN_CALLS))
                .willReturn((i + 59) % 2 == 0);
        given(properties.getBooleanProperty(HEDERA_ALLOWANCES_IS_ENABLED))
                .willReturn((i + 60) % 2 == 0);
        given(properties.getTypesProperty(AUTO_RENEW_TARGET_TYPES)).willReturn(typesFor(i));
        given(properties.getBooleanProperty(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS))
                .willReturn((i + 60) % 2 == 0);
        given(properties.getBooleanProperty(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE))
                .willReturn((i + 61) % 2 == 0);
        given(properties.getBlockValuesProperty(CONTRACTS_KNOWN_BLOCK_HASH))
                .willReturn(blockValues);
        given(properties.getLongProperty(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST))
                .willReturn(i + 64L);
        given(properties.getLongProperty(SCHEDULING_MAX_TXN_PER_SEC)).willReturn(i + 65L);
        given(properties.getLongProperty(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT))
                .willReturn(i + 66L);
        given(properties.getLongProperty(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS))
                .willReturn(i + 67L);
        given(properties.getLongProperty(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS))
                .willReturn(i + 68L);
        given(properties.getLongProperty(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS))
                .willReturn(i + 69L);
        given(properties.getLongProperty(STAKING_START_THRESH)).willReturn(i + 70L);
        given(properties.getIntProperty(STAKING_FEES_NODE_REWARD_PERCENT)).willReturn(i + 71);
        given(properties.getIntProperty(STAKING_FEES_STAKING_REWARD_PERCENT)).willReturn(i + 72);
        given(properties.getLongProperty(STAKING_REWARD_RATE)).willReturn(i + 74L);
        given(properties.getBooleanProperty(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS))
                .willReturn((i + 65) % 2 == 0);
        given(properties.getLongProperty(STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR))
                .willReturn(i + 75L);
        given(properties.getBooleanProperty(STAKING_IS_ENABLED)).willReturn((i + 73) % 2 == 0);
        given(properties.getIntProperty(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION))
                .willReturn((i + 77));
        given(properties.getIntProperty(HEDERA_RECORD_STREAM_SIG_FILE_VERSION))
                .willReturn((i + 78));
        given(properties.getLongProperty(ACCOUNTS_MAX_NUM)).willReturn(i + 79L);
        given(properties.getLongProperty(CONTRACTS_MAX_NUM)).willReturn(i + 80L);
        given(properties.getLongProperty(FILES_MAX_NUM)).willReturn(i + 81L);
        given(properties.getLongProperty(TOKENS_MAX_NUM)).willReturn(i + 82L);
        given(properties.getLongProperty(TOPICS_MAX_NUM)).willReturn(i + 83L);
        given(properties.getLongProperty(SCHEDULING_MAX_NUM)).willReturn(i + 84L);
        given(properties.getLongProperty(TOKENS_MAX_AGGREGATE_RELS)).willReturn(i + 85L);
        given(properties.getBooleanProperty(UTIL_PRNG_IS_ENABLED)).willReturn((i + 86) % 2 == 0);
        given(properties.getSidecarsProperty(CONTRACTS_SIDECARS))
                .willReturn(
                        (i + 87) % 2 == 0
                                ? Set.of(SidecarType.CONTRACT_STATE_CHANGE)
                                : Set.of(SidecarType.CONTRACT_BYTECODE));
        given(properties.getBooleanProperty(STAKING_REQUIRE_MIN_STAKE_TO_REWARD))
                .willReturn((i + 79) % 2 == 0);
        given(properties.getNodeStakeRatiosProperty(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS))
                .willReturn(Map.of(0L, 4L, 1L, 8L));
        given(properties.getIntProperty(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB))
                .willReturn((i + 88));
        given(properties.getBooleanProperty(HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION))
                .willReturn((i + 81) % 2 == 0);
        given(properties.getBooleanProperty(CONTRACTS_ITEMIZE_STORAGE_FEES))
                .willReturn((i + 79) % 2 == 1);
        given(properties.getLongProperty(CONTRACTS_REFERENCE_SLOT_LIFETIME)).willReturn(i + 86L);
        given(properties.getIntProperty(CONTRACTS_FREE_STORAGE_TIER_LIMIT)).willReturn(i + 87);
        given(properties.getStringProperty(CONTRACTS_STORAGE_SLOT_PRICE_TIERS))
                .willReturn("0til100M,2000til450M");
        given(properties.getBooleanProperty(HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION))
                .willReturn((i + 82) % 2 == 0);
        given(properties.getBooleanProperty(TOKENS_AUTO_CREATIONS_ENABLED))
                .willReturn((i + 83) % 2 == 0);
        given(properties.getBooleanProperty(CONTRACTS_DYNAMIC_EVM_VERSION)).willReturn(i % 2 == 0);
        given(properties.getStringProperty(CONTRACTS_EVM_VERSION)).willReturn(evmVersions[i % 2]);
        given(properties.getBooleanProperty(BALANCES_COMPRESS_ON_CREATION))
                .willReturn((i + 84) % 2 == 0);
        given(properties.getBooleanProperty(HEDERA_RECORD_STREAM_ENABLE_TRACEABILITY_MIGRATION))
                .willReturn((i + 85) % 2 == 0);
    }

    private Set<EntityType> typesFor(final int i) {
        if (i == 3) {
            return EnumSet.noneOf(EntityType.class);
        } else {
            return ((i + 61) % 2 == 0
                    ? EnumSet.of(EntityType.TOKEN)
                    : EnumSet.of(EntityType.ACCOUNT, EntityType.CONTRACT));
        }
    }

    private AccountID accountWith(final long shard, final long realm, final long num) {
        return AccountID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setAccountNum(num)
                .build();
    }
}
