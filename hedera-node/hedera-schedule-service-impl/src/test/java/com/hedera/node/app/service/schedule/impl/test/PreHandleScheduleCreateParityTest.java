/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.schedule.impl.test;

import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.schedule.impl.SchedulePreTransactionHandlerImpl;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.AdapterUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PreHandleScheduleCreateParityTest {
    private TransactionMetadata metadata;
    private PreHandleDispatcher dispatcher;
    private SchedulePreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        final var now = Instant.now();
        final var accountStore = AdapterUtils.wellKnownKeyLookupAt(now);
        subject = new SchedulePreTransactionHandlerImpl(accountStore);
    }

    @Test
    void scheduleCreateXferWithAdmin() {
        metadata = Mockito.mock(TransactionMetadata.class);
        given(dispatcher.dispatch(any(), any())).willReturn(metadata);
        final var theTxn = txnFrom(SCHEDULE_CREATE_XFER_WITH_ADMIN);
        final var meta =
                subject.preHandleCreateSchedule(
                        theTxn, theTxn.getTransactionID().getAccountID(), dispatcher);
    }

    private void withMockDispatcher() {
        dispatcher = Mockito.mock(PreHandleDispatcher.class);
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
