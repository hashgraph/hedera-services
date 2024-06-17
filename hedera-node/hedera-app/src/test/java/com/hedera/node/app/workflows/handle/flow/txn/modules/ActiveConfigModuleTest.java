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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveConfigModuleTest {
    @Mock
    private ConfigProvider configProvider;

    @Test
    void getConfigurationFromProvider() {
        final var config = new VersionedConfigImpl(DEFAULT_CONFIG, 1L);

        given(configProvider.getConfiguration()).willReturn(config);

        assertThat(ActiveConfigModule.provideConfiguration(configProvider)).isSameAs(config);
    }

    @Test
    void getsHederaConfig() {
        assertThat(ActiveConfigModule.provideHederaConfig(DEFAULT_CONFIG)).isNotNull();
    }
}
