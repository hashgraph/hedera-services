/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.config.TransactionConfig_;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.system.status.DefaultPlatformStatusNexus;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusNexus;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.test.fixtures.event.TransactionUtils;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SwirldTransactionSubmitterTest {

    private TransactionConfig transactionConfig;

    private Random random;

    private SwirldTransactionSubmitter transactionSubmitter;
    private PlatformStatusNexus statusNexus;

    @BeforeAll
    public static void staticSetup() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.system.transaction");
    }

    @BeforeEach
    void setup() {
        random = RandomUtils.getRandom();
        final Configuration configuration = new TestConfigBuilder()
                .withValue(TransactionConfig_.TRANSACTION_MAX_BYTES, 6144)
                .getOrCreateConfig();
        transactionConfig = configuration.getConfigData(TransactionConfig.class);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        statusNexus = new DefaultPlatformStatusNexus(platformContext);

        transactionSubmitter = new SwirldTransactionSubmitter(
                statusNexus, transactionConfig, (t) -> true, mock(TransactionMetrics.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000, 6144, 6145, 8000})
    void testSwirldTransactionSize(final int contentSize) {
        statusNexus.setCurrentStatus(PlatformStatus.ACTIVE);

        final byte[] nbyte = new byte[contentSize];
        random.nextBytes(nbyte);
        if (contentSize <= transactionConfig.transactionMaxBytes()) {
            assertTrue(
                    transactionSubmitter.submitTransaction(new SwirldTransaction(nbyte)), "create transaction failed");
        } else {
            assertFalse(
                    transactionSubmitter.submitTransaction(new SwirldTransaction(nbyte)),
                    "oversize transaction should not be accepted");
        }
    }

    @ParameterizedTest
    @EnumSource(PlatformStatus.class)
    @DisplayName("Transaction denied when not ACTIVE")
    void testPlatformStatus(final PlatformStatus status) {
        statusNexus.setCurrentStatus(status);
        if (PlatformStatus.ACTIVE.equals(status)) {
            assertTrue(
                    transactionSubmitter.submitTransaction(TransactionUtils.randomSwirldTransaction(random)),
                    "Transactions should be accepted when the platform is ACTIVE.");
        } else {
            assertFalse(
                    transactionSubmitter.submitTransaction(TransactionUtils.randomSwirldTransaction(random)),
                    "Transactions should not be accepted when the platform is not ACTIVE.");
        }
    }

    @Test
    void testNullTransactionRejected() {
        assertFalse(transactionSubmitter.submitTransaction(null), "Null transactions should be rejected.");
    }
}
