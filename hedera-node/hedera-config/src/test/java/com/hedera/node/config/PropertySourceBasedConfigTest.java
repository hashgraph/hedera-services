/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.streams.SidecarType;
import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.config.converter.AccountIDConverter;
import com.hedera.node.config.converter.BytesConverter;
import com.hedera.node.config.converter.CongestionMultipliersConverter;
import com.hedera.node.config.converter.ContractIDConverter;
import com.hedera.node.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.config.converter.FileIDConverter;
import com.hedera.node.config.converter.KnownBlockValuesConverter;
import com.hedera.node.config.converter.PermissionedAccountsRangeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.types.CongestionMultipliers;
import com.hedera.node.config.types.EntityScaleFactors;
import com.hedera.node.config.types.EntityType;
import com.hedera.node.config.types.PermissionedAccountsRange;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertySourceBasedConfigTest {

    private final Map<String, String> rawData = new HashMap<>();

    @BeforeEach
    void configureMockForConfigData() {
        rawData.put("test.congestionMultipliers", "90,11x,95,27x,99,103x");
        rawData.put("test.entityScaleFactors", "DEFAULT(32,7:1)");
        rawData.put("test.entityType", "CONTRACT");
        rawData.put("test.knownBlockValues", "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666");
        rawData.put("test.mapAccessType", "ACCOUNTS_GET");
        rawData.put("test.recomputeType", "PENDING_REWARDS");
        rawData.put("test.scaleFactor", "1:3");
        rawData.put("test.accountID", "1.2.3");
        rawData.put("test.contractID", "1.2.3");
        rawData.put("test.fileID", "1.2.3");
        rawData.put("test.hederaFunctionality", "CRYPTO_TRANSFER");
        rawData.put("test.profile", "DEV");
        rawData.put("test.sidecarType", "CONTRACT_ACTION");
    }

    @Test
    void testConfig() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(CongestionMultipliers.class, new CongestionMultipliersConverter())
                .withConverter(EntityScaleFactors.class, new EntityScaleFactorsConverter())
                .withConverter(KnownBlockValues.class, new KnownBlockValuesConverter())
                .withConverter(PermissionedAccountsRange.class, new PermissionedAccountsRangeConverter())
                .withConverter(ScaleFactor.class, new ScaleFactorConverter())
                .withConverter(AccountID.class, new AccountIDConverter())
                .withConverter(ContractID.class, new ContractIDConverter())
                .withConverter(FileID.class, new FileIDConverter())
                .withConverter(Bytes.class, new BytesConverter())
                .build();

        // when
        final CongestionMultipliers congestionMultipliers =
                configuration.getValue("test.congestionMultipliers", CongestionMultipliers.class);
        final EntityScaleFactors entityScaleFactors =
                configuration.getValue("test.entityScaleFactors", EntityScaleFactors.class);
        final EntityType entityType = configuration.getValue("test.entityType", EntityType.class);
        final KnownBlockValues knownBlockValues =
                configuration.getValue("test.knownBlockValues", KnownBlockValues.class);
        final ScaleFactor scaleFactor = configuration.getValue("test.scaleFactor", ScaleFactor.class);
        final AccountID accountID = configuration.getValue("test.accountID", AccountID.class);
        final ContractID contractID = configuration.getValue("test.contractID", ContractID.class);
        final FileID fileID = configuration.getValue("test.fileID", FileID.class);
        final HederaFunctionality hederaFunctionality =
                configuration.getValue("test.hederaFunctionality", HederaFunctionality.class);
        final String profile = configuration.getValue("test.profile", String.class);
        final SidecarType sidecarType = configuration.getValue("test.sidecarType", SidecarType.class);

        // then
        assertThat(congestionMultipliers).isNotNull();
        assertThat(entityScaleFactors).isNotNull();
        assertThat(entityType).isNotNull();
        assertThat(knownBlockValues).isNotNull();
        assertThat(scaleFactor).isNotNull();
        assertThat(accountID).isNotNull();
        assertThat(contractID).isNotNull();
        assertThat(fileID).isNotNull();
        assertThat(hederaFunctionality).isNotNull();
        assertThat(profile).isNotNull();
        assertThat(sidecarType).isNotNull();
    }
}
