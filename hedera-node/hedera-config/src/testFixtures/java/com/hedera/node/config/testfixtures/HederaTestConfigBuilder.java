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

package com.hedera.node.config.testfixtures;

import com.hedera.node.config.converter.AccountIDConverter;
import com.hedera.node.config.converter.BytesConverter;
import com.hedera.node.config.converter.CongestionMultipliersConverter;
import com.hedera.node.config.converter.ContractIDConverter;
import com.hedera.node.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.config.converter.EntityTypeConverter;
import com.hedera.node.config.converter.FileIDConverter;
import com.hedera.node.config.converter.HederaFunctionalityConverter;
import com.hedera.node.config.converter.KeyValuePairConverter;
import com.hedera.node.config.converter.KnownBlockValuesConverter;
import com.hedera.node.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.config.converter.MapAccessTypeConverter;
import com.hedera.node.config.converter.PermissionedAccountsRangeConverter;
import com.hedera.node.config.converter.RecomputeTypeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.converter.SemanticVersionConverter;
import com.hedera.node.config.converter.SidecarTypeConverter;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A builder for creating {@link TestConfigBuilder} instances for testing.
 */
public final class HederaTestConfigBuilder {

    private HederaTestConfigBuilder() {}

    /**
     * Creates a new {@link TestConfigBuilder} instance that has automatically registered all config records that are
     * part of the base packages {@code com.hedera} or {@code com.swirlds}.
     *
     * @return the new {@link TestConfigBuilder} instance
     */
    @NonNull
    public static TestConfigBuilder create() {
        return create(true);
    }

    /**
     * Creates a new {@link TestConfigBuilder} instance. If the {@code registerAllTypes} param is true all config
     * records that are part of the base packages {@code com.hedera} or {@code com.swirlds} are automatically
     * registered. If false, no config record is registered.
     *
     * @param registerAllTypes defines if all config records that are part of the base packages {@code com.hedera} or
     *                         {@code com.swirlds} should automatically be registered
     * @return the new {@link TestConfigBuilder} instance
     */
    @NonNull
    public static TestConfigBuilder create(boolean registerAllTypes) {
        return new TestConfigBuilder(registerAllTypes)
                .withConverter(new AccountIDConverter())
                .withConverter(new BytesConverter())
                .withConverter(new CongestionMultipliersConverter())
                .withConverter(new ContractIDConverter())
                .withConverter(new EntityScaleFactorsConverter())
                .withConverter(new EntityTypeConverter())
                .withConverter(new FileIDConverter())
                .withConverter(new HederaFunctionalityConverter())
                .withConverter(new KeyValuePairConverter())
                .withConverter(new KnownBlockValuesConverter())
                .withConverter(new LegacyContractIdActivationsConverter())
                .withConverter(new MapAccessTypeConverter())
                .withConverter(new PermissionedAccountsRangeConverter())
                .withConverter(new RecomputeTypeConverter())
                .withConverter(new ScaleFactorConverter())
                .withConverter(new SemanticVersionConverter())
                .withConverter(new SidecarTypeConverter())
                .withValidator(new EmulatesMapValidator());
    }

    /**
     * Creates a new {@link Configuration} instance that has automatically registered all config records that are part
     * of the base packages {@code com.hedera} or {@code com.swirlds}.
     *
     * @return a new {@link Configuration} instance
     */
    @NonNull
    public static Configuration createConfig() {
        return createConfig(true);
    }

    /**
     * Creates a new {@link Configuration} instance. If the {@code registerAllTypes} param is true all config records
     * that are part of the base packages {@code com.hedera} or {@code com.swirlds} are automatically registered. If
     * false, no config record is registered.
     *
     * @param registerAllTypes defines if all config records that are part of the base packages {@code com.hedera} or
     *                         {@code com.swirlds} should automatically be registered.
     * @return a new {@link Configuration} instance
     */
    @NonNull
    public static Configuration createConfig(boolean registerAllTypes) {
        return create(registerAllTypes).getOrCreateConfig();
    }
}
