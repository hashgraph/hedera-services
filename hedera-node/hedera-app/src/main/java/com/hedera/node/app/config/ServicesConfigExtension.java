/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.service.mono.context.domain.security.PermissionedAccountsRange;
import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import com.hedera.node.config.converter.AccountIDConverter;
import com.hedera.node.config.converter.BytesConverter;
import com.hedera.node.config.converter.CongestionMultipliersConverter;
import com.hedera.node.config.converter.ContractIDConverter;
import com.hedera.node.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.config.converter.FileIDConverter;
import com.hedera.node.config.converter.FunctionalitySetConverter;
import com.hedera.node.config.converter.KeyValuePairConverter;
import com.hedera.node.config.converter.KnownBlockValuesConverter;
import com.hedera.node.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.config.converter.LongPairConverter;
import com.hedera.node.config.converter.PermissionedAccountsRangeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.converter.SemanticVersionConverter;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.ApiPermissionConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.AutoRenew2Config;
import com.hedera.node.config.data.AutoRenewConfig;
import com.hedera.node.config.data.BalancesConfig;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.CacheConfig;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.CryptoCreateWithAliasConfig;
import com.hedera.node.config.data.DevConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.ExpiryConfig;
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.GrpcConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.NettyConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.config.data.RatesConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hedera.node.config.data.SigsConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.StatsConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.data.TopicsConfig;
import com.hedera.node.config.data.TraceabilityConfig;
import com.hedera.node.config.data.UpgradeConfig;
import com.hedera.node.config.data.UtilPrngConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.types.HederaFunctionalitySet;
import com.hedera.node.config.types.KeyValuePair;
import com.hedera.node.config.types.LongPair;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.ConfigurationExtension;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Sets up configuration for services.
 */
public class ServicesConfigExtension implements ConfigurationExtension {

    /**
     * {@inheritDoc}
     */
    @Override
    public void extendConfiguration(@NonNull final ConfigurationBuilder builder) {
        // Register config data types
        builder.withConfigDataType(AccountsConfig.class)
                .withConfigDataType(ApiPermissionConfig.class)
                .withConfigDataType(AutoCreationConfig.class)
                .withConfigDataType(AutoRenew2Config.class)
                .withConfigDataType(AutoRenewConfig.class)
                .withConfigDataType(BalancesConfig.class)
                .withConfigDataType(BlockRecordStreamConfig.class)
                .withConfigDataType(BootstrapConfig.class)
                .withConfigDataType(CacheConfig.class)
                .withConfigDataType(ConsensusConfig.class)
                .withConfigDataType(ContractsConfig.class)
                .withConfigDataType(CryptoCreateWithAliasConfig.class)
                .withConfigDataType(DevConfig.class)
                .withConfigDataType(EntitiesConfig.class)
                .withConfigDataType(ExpiryConfig.class)
                .withConfigDataType(FeesConfig.class)
                .withConfigDataType(FilesConfig.class)
                .withConfigDataType(GrpcConfig.class)
                .withConfigDataType(HederaConfig.class)
                .withConfigDataType(LazyCreationConfig.class)
                .withConfigDataType(LedgerConfig.class)
                .withConfigDataType(NettyConfig.class)
                .withConfigDataType(NetworkAdminConfig.class)
                .withConfigDataType(RatesConfig.class)
                .withConfigDataType(SchedulingConfig.class)
                .withConfigDataType(SigsConfig.class)
                .withConfigDataType(StakingConfig.class)
                .withConfigDataType(StatsConfig.class)
                .withConfigDataType(TokensConfig.class)
                .withConfigDataType(TopicsConfig.class)
                .withConfigDataType(TraceabilityConfig.class)
                .withConfigDataType(UpgradeConfig.class)
                .withConfigDataType(UtilPrngConfig.class)
                .withConfigDataType(VersionConfig.class);

        // Register converters
        builder.withConverter(CongestionMultipliers.class, new CongestionMultipliersConverter())
                .withConverter(EntityScaleFactors.class, new EntityScaleFactorsConverter())
                .withConverter(KnownBlockValues.class, new KnownBlockValuesConverter())
                .withConverter(LegacyContractIdActivations.class, new LegacyContractIdActivationsConverter())
                .withConverter(ScaleFactor.class, new ScaleFactorConverter())
                .withConverter(AccountID.class, new AccountIDConverter())
                .withConverter(ContractID.class, new ContractIDConverter())
                .withConverter(FileID.class, new FileIDConverter())
                .withConverter(PermissionedAccountsRange.class, new PermissionedAccountsRangeConverter())
                .withConverter(SemanticVersion.class, new SemanticVersionConverter())
                .withConverter(LongPair.class, new LongPairConverter())
                .withConverter(KeyValuePair.class, new KeyValuePairConverter())
                .withConverter(HederaFunctionalitySet.class, new FunctionalitySetConverter())
                .withConverter(Bytes.class, new BytesConverter());

        // register validators
        builder.withValidator(new EmulatesMapValidator());
    }
}
