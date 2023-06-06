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
import com.hedera.node.config.converter.ProfileConverter;
import com.hedera.node.config.converter.RecomputeTypeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.converter.SemanticVersionConverter;
import com.hedera.node.config.converter.SidecarTypeConverter;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.swirlds.test.framework.config.TestConfigBuilder;

/**
 * A builder for creating {@link TestConfigBuilder} instances for testing.
 */
public final class HederaTestConfigBuilder {

    private HederaTestConfigBuilder() {}

    public static TestConfigBuilder create() {
        return new TestConfigBuilder();
    }

    public static TestConfigBuilder create(boolean registerAllTypes) {
        return new TestConfigBuilder(registerAllTypes)
                .withConverter(new CongestionMultipliersConverter())
                .withConverter(new EntityScaleFactorsConverter())
                .withConverter(new EntityTypeConverter())
                .withConverter(new KnownBlockValuesConverter())
                .withConverter(new LegacyContractIdActivationsConverter())
                .withConverter(new MapAccessTypeConverter())
                .withConverter(new RecomputeTypeConverter())
                .withConverter(new ScaleFactorConverter())
                .withConverter(new AccountIDConverter())
                .withConverter(new ContractIDConverter())
                .withConverter(new FileIDConverter())
                .withConverter(new HederaFunctionalityConverter())
                .withConverter(new ProfileConverter())
                .withConverter(new SidecarTypeConverter())
                .withConverter(new KeyValuePairConverter())
                .withConverter(new BytesConverter())
                .withConverter(new SemanticVersionConverter())
                .withValidator(new EmulatesMapValidator());
    }
}
