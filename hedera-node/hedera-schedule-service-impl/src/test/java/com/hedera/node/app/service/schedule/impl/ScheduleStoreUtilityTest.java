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

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.security.InvalidKeyException;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleStoreUtilityTest extends ScheduleStoreTestBase {

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    void verifyHashCalculationNormalFunction() {
        final String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);
    }

    @Test
    void verifyIncludedFieldsChangeHash() {
        String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);

        BDDMockito.given(scheduleInState.providedExpirationSecond()).willReturn(0L);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_0_EXPIRE_SHA256);
        BDDMockito.given(scheduleInState.providedExpirationSecond()).willCallRealMethod();

        BDDMockito.given(scheduleInState.adminKey()).willReturn(payerKey);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_PAYER_IS_ADMIN_SHA256);
        BDDMockito.given(scheduleInState.adminKey()).willCallRealMethod();

        BDDMockito.given(scheduleInState.scheduledTransaction()).willReturn(createAlternateScheduled());
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_ALTERNATE_SCHEDULED_SHA256);
        BDDMockito.given(scheduleInState.scheduledTransaction()).willCallRealMethod();

        BDDMockito.given(scheduleInState.payerAccountId()).willReturn(adminAccountId);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_ADMIN_IS_PAYER_SHA256);
        BDDMockito.given(scheduleInState.payerAccountId()).willCallRealMethod();

        BDDMockito.given(scheduleInState.schedulerAccountId()).willReturn(payerAccountId);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_PAYER_IS_SCHEDULER_SHA256);
        BDDMockito.given(scheduleInState.schedulerAccountId()).willCallRealMethod();

        BDDMockito.given(scheduleInState.memo()).willReturn(ODD_MEMO);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_ODD_MEMO_SHA256);
        BDDMockito.given(scheduleInState.memo()).willCallRealMethod();

        final boolean originalWait = scheduleInState.waitForExpiry();
        BDDMockito.given(scheduleInState.waitForExpiry()).willReturn(!originalWait);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isNotEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_WAIT_EXPIRE_SHA256);
        BDDMockito.given(scheduleInState.waitForExpiry()).willCallRealMethod();
    }

    @Test
    void verifyExcludedAttributesHaveNoEffect() {
        String hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);

        BDDMockito.given(scheduleInState.scheduleId()).willReturn(new ScheduleID(42L, 444L, 22740229L));
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.scheduleId()).willCallRealMethod();

        BDDMockito.given(scheduleInState.calculatedExpirationSecond()).willReturn(18640811L);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.calculatedExpirationSecond()).willCallRealMethod();

        BDDMockito.given(scheduleInState.deleted()).willReturn(true);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.deleted()).willCallRealMethod();

        BDDMockito.given(scheduleInState.executed()).willReturn(true);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.executed()).willCallRealMethod();

        BDDMockito.given(scheduleInState.resolutionTime()).willReturn(modifiedResolutionTime);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.resolutionTime()).willCallRealMethod();

        BDDMockito.given(scheduleInState.scheduleValidStart()).willReturn(modifiedStartTime);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.scheduleValidStart()).willCallRealMethod();

        BDDMockito.given(scheduleInState.originalCreateTransaction()).willReturn(alternateCreateTransaction);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.originalCreateTransaction()).willCallRealMethod();

        BDDMockito.given(scheduleInState.signatories()).willReturn(alternateSignatories);
        hashValue = ScheduleStoreUtility.calculateStringHash(scheduleInState);
        BDDAssertions.assertThat(hashValue).isEqualTo(SCHEDULE_IN_STATE_SHA256);
        BDDMockito.given(scheduleInState.signatories()).willCallRealMethod();
    }
}
