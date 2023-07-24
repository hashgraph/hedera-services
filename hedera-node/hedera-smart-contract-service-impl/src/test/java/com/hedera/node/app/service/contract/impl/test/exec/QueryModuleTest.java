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

package com.hedera.node.app.service.contract.impl.test.exec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.QueryModule;
import com.hedera.node.app.service.contract.impl.exec.TransactionModule;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryModuleTest {
    @Mock
    private QueryContext context;

    @Test
    void providesExpectedConfig() {
        final var config = HederaTestConfigBuilder.create().getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        assertSame(config, QueryModule.provideConfiguration(context));
        assertNotNull(TransactionModule.provideContractsConfig(config));
    }

    @Test
    void providesApproximatelyNowForConsensusTime() {
        final var then = QueryModule.provideConsensusTime();
        assertFalse(then.isAfter(Instant.now()));
    }
}
