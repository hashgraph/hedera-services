/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import org.junit.jupiter.api.Test;

class ConfigAliasesTest {
    @Test
    void testAliases() {
        final int valueNonAncient = 10;
        final int valueExpired = 20;
        final int valueCoin = 30;

        final SimpleConfigSource configSource = new SimpleConfigSource()
                .withValue("state.roundsNonAncient", String.valueOf(valueNonAncient))
                .withValue("state.roundsExpired", String.valueOf(valueExpired))
                .withValue("coinFreq", String.valueOf(valueCoin));
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(ConfigAliases.addConfigAliases(configSource))
                .withConfigDataType(ConsensusConfig.class)
                .build();

        final ConsensusConfig consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        assertEquals(valueNonAncient, consensusConfig.roundsNonAncient());
        assertEquals(valueExpired, consensusConfig.roundsExpired());
        assertEquals(valueCoin, consensusConfig.coinFreq());
    }
}
