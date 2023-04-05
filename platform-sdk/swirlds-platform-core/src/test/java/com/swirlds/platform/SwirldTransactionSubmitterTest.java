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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.metrics.TransactionMetrics;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SwirldTransactionSubmitterTest {

    private final SettingsProvider settings = mock(SettingsProvider.class);

    private Random random;

    private PlatformStatus platformStatus;

    private SwirldTransactionSubmitter transactionSubmitter;

    private static Stream<Arguments> zeroStakeParams() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }

    @BeforeAll
    public static void staticSetup() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.system.transaction");
        SettingsCommon.transactionMaxBytes = 6144;
    }

    @BeforeEach
    void setup() {
        random = RandomUtils.getRandom();

        when(settings.getTransactionMaxBytes()).thenReturn(SettingsCommon.transactionMaxBytes);

        platformStatus = PlatformStatus.ACTIVE;

        transactionSubmitter = new SwirldTransactionSubmitter(
                this::getPlatformStatus, settings, (t) -> true, mock(TransactionMetrics.class));
    }

    private PlatformStatus getPlatformStatus() {
        return platformStatus;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000, 6144, 6145, 8000})
    void testSwirldTransactionSize(final int contentSize) {
        final byte[] nbyte = new byte[contentSize];
        random.nextBytes(nbyte);
        if (contentSize <= SettingsCommon.transactionMaxBytes) {
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
        platformStatus = status;
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
