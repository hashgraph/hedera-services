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

package com.hedera.node.app.service.schedule.impl;

import static org.assertj.core.api.BDDAssertions.assertThat;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.security.InvalidKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleStoreUtilityTest extends ScheduleTestBase {

    @BeforeEach
    void setup() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    void verifyHashCalculationNormalFunction() {
        final String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);
    }

    @Test
    void verifyIncludedFieldsChangeHash() {
        Schedule.Builder testSchedule = scheduleInState.copyBuilder();

        String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);

        testSchedule.providedExpirationSecond(0L);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);
        testSchedule.providedExpirationSecond(scheduleInState.providedExpirationSecond());

        testSchedule.adminKey(payerKey);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_PAYER_IS_ADMIN_SHA256);
        testSchedule.adminKey(scheduleInState.adminKey());

        testSchedule.scheduledTransaction(createAlternateScheduled());
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_ALTERNATE_SCHEDULED_SHA256);
        testSchedule.scheduledTransaction(scheduleInState.scheduledTransaction());

        testSchedule.memo(ODD_MEMO);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_ODD_MEMO_SHA256);
        testSchedule.memo(scheduleInState.memo());

        testSchedule.waitForExpiry(!scheduleInState.waitForExpiry());
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_WAIT_EXPIRE_SHA256);
        testSchedule.waitForExpiry(scheduleInState.waitForExpiry());
    }

    @Test
    void verifyExcludedAttributesHaveNoEffect() {
        Schedule.Builder testSchedule = scheduleInState.copyBuilder();

        String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);

        testSchedule.scheduleId(new ScheduleID(42L, 444L, 22740229L));
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.scheduleId(scheduleInState.scheduleId());

        testSchedule.calculatedExpirationSecond(18640811L);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.calculatedExpirationSecond(scheduleInState.calculatedExpirationSecond());

        testSchedule.deleted(!scheduleInState.deleted());
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.deleted(scheduleInState.deleted());

        testSchedule.executed(!scheduleInState.executed());
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.executed(scheduleInState.executed());

        testSchedule.payerAccountId(admin);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.payerAccountId(scheduleInState.payerAccountId());

        testSchedule.schedulerAccountId(payer);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.schedulerAccountId(scheduleInState.schedulerAccountId());

        testSchedule.resolutionTime(modifiedResolutionTime);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.resolutionTime(scheduleInState.resolutionTime());

        testSchedule.scheduleValidStart(modifiedStartTime);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.scheduleValidStart(scheduleInState.scheduleValidStart());

        testSchedule.originalCreateTransaction(alternateCreateTransaction);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.originalCreateTransaction(scheduleInState.originalCreateTransaction());

        testSchedule.signatories(alternateSignatories);
        hashValue = ScheduleStoreUtility.calculateStringHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        testSchedule.signatories(scheduleInState.signatories());
    }
}
