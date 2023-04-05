/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.utils;

import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.node.app.service.evm.utils.ValidationUtils;
import com.hedera.node.app.service.mono.context.domain.security.PermissionFileUtils;
import com.hedera.node.app.service.mono.context.properties.PropUtils;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalExecutor;
import com.hedera.node.app.service.mono.contracts.gascalculator.GasCalculatorHederaUtil;
import com.hedera.node.app.service.mono.contracts.operation.HederaOperationUtil;
import com.hedera.node.app.service.mono.contracts.sources.AddressKeyedMapFactory;
import com.hedera.node.app.service.mono.fees.calculation.FeeCalcUtils;
import com.hedera.node.app.service.mono.fees.calculation.consensus.ConsensusFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.contract.ContractFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.crypto.CryptoFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.ethereum.EthereumFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.file.FileFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.meta.FixedUsageEstimates;
import com.hedera.node.app.service.mono.fees.calculation.schedule.ScheduleFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.token.TokenFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.utils.TriggeredValuesParser;
import com.hedera.node.app.service.mono.files.HFileMetaSerde;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.grpc.marshalling.AdjustmentUtils;
import com.hedera.node.app.service.mono.keys.HederaKeyActivation;
import com.hedera.node.app.service.mono.keys.HederaKeyTraversal;
import com.hedera.node.app.service.mono.keys.KeysModule;
import com.hedera.node.app.service.mono.keys.RevocationServiceCharacteristics;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakingUtils;
import com.hedera.node.app.service.mono.queries.QueriesModule;
import com.hedera.node.app.service.mono.sigs.HederaToPlatformSigOps;
import com.hedera.node.app.service.mono.sigs.PlatformSigOps;
import com.hedera.node.app.service.mono.sigs.factories.PlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.metadata.TokenMetaUtils;
import com.hedera.node.app.service.mono.sigs.utils.ImmutableKeyUtils;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.service.mono.sigs.utils.PrecheckUtils;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils;
import com.hedera.node.app.service.mono.state.merkle.internals.ByteUtils;
import com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk;
import com.hedera.node.app.service.mono.state.migration.ReleaseThirtyMigration;
import com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.state.migration.StateVersions;
import com.hedera.node.app.service.mono.state.serdes.IoUtils;
import com.hedera.node.app.service.mono.state.virtual.IterableStorageUtils;
import com.hedera.node.app.service.mono.state.virtual.KeyPackingUtils;
import com.hedera.node.app.service.mono.state.virtual.utils.EntityIoUtils;
import com.hedera.node.app.service.mono.stats.ExpiryStats;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.stats.MiscSpeedometers;
import com.hedera.node.app.service.mono.stats.ServicesStatsConfig;
import com.hedera.node.app.service.mono.stats.StatsModule;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompileUtils;
import com.hedera.node.app.service.mono.store.models.TopicConversion;
import com.hedera.node.app.service.mono.throttling.ThrottlingModule;
import com.hedera.node.app.service.mono.txns.consensus.ConsensusLogicModule;
import com.hedera.node.app.service.mono.txns.contract.ContractLogicModule;
import com.hedera.node.app.service.mono.txns.crypto.CryptoLogicModule;
import com.hedera.node.app.service.mono.txns.ethereum.EthereumLogicModule;
import com.hedera.node.app.service.mono.txns.file.FileLogicModule;
import com.hedera.node.app.service.mono.txns.network.NetworkLogicModule;
import com.hedera.node.app.service.mono.txns.schedule.ScheduleLogicModule;
import com.hedera.node.app.service.mono.txns.submission.PresolvencyFlaws;
import com.hedera.node.app.service.mono.txns.submission.SubmissionModule;
import com.hedera.node.app.service.mono.txns.token.TokenLogicModule;
import com.hedera.node.app.service.mono.txns.token.TokenOpsValidator;
import com.hedera.node.app.service.mono.txns.token.process.NewRels;
import com.hedera.node.app.service.mono.txns.util.TokenUpdateValidator;
import com.hedera.node.app.service.mono.txns.util.UtilLogicModule;
import com.hedera.node.app.service.mono.txns.validation.PureValidation;
import com.hedera.node.app.service.mono.txns.validation.TokenListChecks;
import com.hedera.node.app.service.mono.utils.forensics.OrderedComparison;
import com.hedera.node.app.service.mono.utils.forensics.RecordParsers;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UtilsConstructorTest {
    private static final Set<Class<?>> toBeTested = new HashSet<>(Arrays.asList(
            EntityIoUtils.class,
            OrderedComparison.class,
            RecordParsers.class,
            Units.class,
            AbiConstants.class,
            MapValueListUtils.class,
            HFileMetaSerde.class,
            IoUtils.class,
            DescriptorUtils.class,
            TokenMetaUtils.class,
            MiscCryptoUtils.class,
            NewRels.class,
            PermissionFileUtils.class,
            PropUtils.class,
            AddressKeyedMapFactory.class,
            ValidationUtils.class,
            FeeCalcUtils.class,
            FixedUsageEstimates.class,
            AdjustmentUtils.class,
            HederaKeyActivation.class,
            HederaKeyTraversal.class,
            RevocationServiceCharacteristics.class,
            HederaToPlatformSigOps.class,
            PlatformSigOps.class,
            PlatformSigFactory.class,
            ImmutableKeyUtils.class,
            PrecheckUtils.class,
            MerkleAccount.ChildIndices.class,
            BitPackUtils.class,
            MapMigrationToDisk.class,
            ReleaseThirtyMigration.class,
            StateChildIndices.class,
            StateVersions.class,
            ExpiryStats.Names.class,
            ExpiryStats.Descriptions.class,
            MiscRunningAvgs.Names.class,
            MiscRunningAvgs.Descriptions.class,
            MiscSpeedometers.Names.class,
            MiscSpeedometers.Descriptions.class,
            ServicesStatsConfig.class,
            PresolvencyFlaws.class,
            PureValidation.class,
            TokenListChecks.class,
            EntityIdUtils.class,
            HederaDateTimeFormatter.class,
            TokenTypesMapper.class,
            UnzipUtility.class,
            MiscUtils.class,
            TriggeredValuesParser.class,
            MetadataMapFactory.class,
            TokenOpsValidator.class,
            SubmissionModule.class,
            ConsensusFeesModule.class,
            ContractFeesModule.class,
            EthereumFeesModule.class,
            CryptoFeesModule.class,
            FileFeesModule.class,
            ScheduleFeesModule.class,
            TokenFeesModule.class,
            KeysModule.class,
            QueriesModule.class,
            StatsModule.class,
            ThrottlingModule.class,
            ConsensusLogicModule.class,
            ContractLogicModule.class,
            CryptoLogicModule.class,
            FileLogicModule.class,
            NetworkLogicModule.class,
            ScheduleLogicModule.class,
            TokenLogicModule.class,
            TopicConversion.class,
            CallLocalExecutor.class,
            HederaOperationUtil.class,
            GasCalculatorHederaUtil.class,
            SerializationUtils.class,
            KeyPackingUtils.class,
            IterableStorageUtils.class,
            EthereumLogicModule.class,
            StakingInfoMapBuilder.class,
            ByteUtils.class,
            Units.class,
            StakingUtils.class,
            UtilLogicModule.class,
            PrecompileUtils.class,
            TokenUpdateValidator.class));

    @Test
    void throwsInConstructor() {
        for (final var clazz : toBeTested) {
            assertFor(clazz);
        }
    }

    private static final String UNEXPECTED_THROW = "Unexpected `%s` was thrown in `%s` constructor!";
    private static final String NO_THROW = "No exception was thrown in `%s` constructor!";

    private void assertFor(final Class<?> clazz) {
        try {
            final var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);

            constructor.newInstance();
        } catch (final InvocationTargetException expected) {
            final var cause = expected.getCause();
            Assertions.assertTrue(
                    cause instanceof UnsupportedOperationException, String.format(UNEXPECTED_THROW, cause, clazz));
            return;
        } catch (final Exception e) {
            Assertions.fail(String.format(UNEXPECTED_THROW, e, clazz));
        }
        Assertions.fail(String.format(NO_THROW, clazz));
    }
}
