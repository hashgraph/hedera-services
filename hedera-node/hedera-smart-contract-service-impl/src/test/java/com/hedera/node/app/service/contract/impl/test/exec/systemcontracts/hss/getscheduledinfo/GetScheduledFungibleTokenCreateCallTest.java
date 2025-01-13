/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.getscheduledinfo;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.LEDGER_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.getscheduledinfo.GetScheduledFungibleTokenCreateCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GetScheduledFungibleTokenCreateCallTest extends CallTestBase {

    @Mock
    private Configuration configuration;

    @Mock
    private LedgerConfig ledgerConfig;

    private GetScheduledFungibleTokenCreateCall subject;
    private final ScheduleID scheduleId =
            ScheduleID.newBuilder().scheduleNum(1L).build();

    @BeforeEach
    void setUp() {
        subject = new GetScheduledFungibleTokenCreateCall(gasCalculator, mockEnhancement(), configuration, scheduleId);
    }

    @Test
    void returnsNotFoundForMissingSchedule() {
        given(nativeOperations.getSchedule(scheduleId.scheduleNum())).willReturn(null);

        Call.PricedResult result = subject.execute(frame);

        assertEquals(RECORD_NOT_FOUND, result.responseCode());
    }

    @Test
    void returnsInvalidForNonTokenCreationSchedule() {
        var schedule = Schedule.newBuilder()
                .scheduledTransaction(SchedulableTransactionBody.newBuilder().freeze(FreezeTransactionBody.DEFAULT))
                .build();
        given(nativeOperations.getSchedule(scheduleId.scheduleNum())).willReturn(schedule);

        final var result = subject.execute(frame);

        assertEquals(INVALID_SCHEDULE_ID, result.responseCode());
    }

    @Test
    void returnsInvalidForNonFungibleTokenSchedule() {
        var schedule = Schedule.newBuilder()
                .scheduledTransaction(SchedulableTransactionBody.newBuilder()
                        .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .build())
                        .build())
                .build();
        given(nativeOperations.getSchedule(scheduleId.scheduleNum())).willReturn(schedule);

        Call.PricedResult result = subject.execute(frame);

        assertEquals(INVALID_SCHEDULE_ID, result.responseCode());
    }

    @Test
    void returnsSuccessForValidFungibleTokenSchedule() {
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.id()).willReturn(Bytes.wrap(LEDGER_ID));

        var schedule = Schedule.newBuilder()
                .scheduledTransaction(SchedulableTransactionBody.newBuilder()
                        .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .expiry(Timestamp.DEFAULT)
                                .autoRenewPeriod(Duration.DEFAULT)
                                .build())
                        .build())
                .build();
        given(nativeOperations.getSchedule(scheduleId.scheduleNum())).willReturn(schedule);

        Call.PricedResult result = subject.execute(frame);

        assertEquals(SUCCESS, result.responseCode());
    }
}
