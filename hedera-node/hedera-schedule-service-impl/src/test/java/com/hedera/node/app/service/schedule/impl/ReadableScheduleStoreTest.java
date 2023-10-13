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

package com.hedera.node.app.service.schedule.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableScheduleStoreTest extends ScheduleTestBase {

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void constructorThrowsIfStatesIsNull() {
        BDDAssertions.assertThatThrownBy(() -> new ReadableScheduleStoreImpl(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getsExpectedSize() {
        assertThat(scheduleStore.numSchedulesInState()).isEqualTo(2);
    }

    @Test
    void returnsEmptyIfMissingSchedule() {
        final long missingId = testScheduleID.scheduleNum() + 15_000L;
        final ScheduleID missing =
                testScheduleID.copyBuilder().scheduleNum(missingId).build();
        assertThat(scheduleStore.get(missing)).isNull();
    }

    @Test
    void getsScheduleMetaFromFetchedSchedule() {
        final Schedule readSchedule = scheduleStore.get(testScheduleID);
        assertThat(readSchedule).isNotNull();
        assertThat(readSchedule.payerAccountId()).isEqualTo(payer);
        assertThat(readSchedule.adminKey()).isEqualTo(adminKey);
        assertThat(readSchedule.scheduledTransaction()).isEqualTo(scheduled);
    }

    @Test
    void getsScheduleMetaFromFetchedScheduleNoExplicitPayer() {
        final Schedule modified =
                scheduleInState.copyBuilder().payerAccountId(nullAccount).build();
        writableSchedules.put(modified);
        final Schedule readSchedule = scheduleStore.get(testScheduleID);
        assertThat(readSchedule).isNotNull();
        assertThat(readSchedule.payerAccountId()).isNull();
        assertThat(readSchedule.adminKey()).isEqualTo(adminKey);
        assertThat(readSchedule.scheduledTransaction()).isEqualTo(scheduled);
    }

    @Test
    void verifyGetByExpiration() {
        final List<Schedule> schedulesBySecond = scheduleStore.getByExpirationSecond(expirationTime.seconds());
        assertThat(schedulesBySecond).hasSize(1).containsExactly(scheduleInState);
        long altTime = testConsensusTime.getEpochSecond() + scheduleConfig.maxExpirationFutureSeconds();
        final List<Schedule> altSchedulesBySecond = scheduleStore.getByExpirationSecond(altTime);
        assertThat(altSchedulesBySecond).hasSize(1).containsExactly(otherScheduleInState);
        final int expandedSize = listOfScheduledOptions.size() + 1;
        final List<Schedule> expanded = new ArrayList<>(expandedSize);
        expanded.add(otherScheduleInState);
        for (Schedule next : listOfScheduledOptions) {
            Schedule.Builder nextWithExpiry = next.copyBuilder();
            nextWithExpiry.providedExpirationSecond(altTime).calculatedExpirationSecond(altTime);
            final Schedule modified = nextWithExpiry.build();
            expanded.add(modified);
            writableSchedules.put(modified);
        }
        // This works because write/read are the same object.  If that changes then we must commit and reset here
        // to update the underlying KV states.
        final List<Schedule> expandedBySecond = scheduleStore.getByExpirationSecond(altTime);
        assertThat(expandedBySecond).hasSize(expandedSize).containsExactlyInAnyOrderElementsOf(expanded);
    }
}
