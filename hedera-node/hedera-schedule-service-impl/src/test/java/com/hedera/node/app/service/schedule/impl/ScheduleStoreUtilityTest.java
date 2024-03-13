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
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.InvalidKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleStoreUtilityTest extends ScheduleTestBase {

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    void verifyIncludedFieldsChangeHash() {
        Schedule.Builder testSchedule = scheduleInState.copyBuilder();

        Bytes origHashValue = ScheduleStoreUtility.calculateBytesHash(scheduleInState);

        // change the expiration time and verify that hash changes
        testSchedule.providedExpirationSecond(0L);
        Bytes hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(origHashValue);
        testSchedule.providedExpirationSecond(scheduleInState.providedExpirationSecond());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        // change the admin key and verify that hash changes
        testSchedule.adminKey(payerKey);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(origHashValue);
        testSchedule.adminKey(scheduleInState.adminKey());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        // change the scheduled transaction and verify that hash changes
        testSchedule.scheduledTransaction(createAlternateScheduled());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(origHashValue);
        testSchedule.scheduledTransaction(scheduleInState.scheduledTransaction());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        // change the memo and verify that hash changes
        testSchedule.memo(ODD_MEMO);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(origHashValue);
        testSchedule.memo(scheduleInState.memo());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        // change the wait for expiry and verify that hash changes
        testSchedule.waitForExpiry(!scheduleInState.waitForExpiry());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isNotEqualTo(origHashValue);
        testSchedule.waitForExpiry(scheduleInState.waitForExpiry());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);
    }

    @Test
    void verifyExcludedAttributesHaveNoEffect() {
        Schedule.Builder testSchedule = scheduleInState.copyBuilder();

        Bytes origHashValue = ScheduleStoreUtility.calculateBytesHash(scheduleInState);

        testSchedule.scheduleId(new ScheduleID(42L, 444L, 22740229L));
        Bytes hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.calculatedExpirationSecond(18640811L);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.deleted(!scheduleInState.deleted());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.executed(!scheduleInState.executed());
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.payerAccountId(admin);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.schedulerAccountId(payer);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.resolutionTime(modifiedResolutionTime);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.scheduleValidStart(modifiedStartTime);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.originalCreateTransaction(alternateCreateTransaction);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);

        testSchedule.signatories(alternateSignatories);
        hashValue = ScheduleStoreUtility.calculateBytesHash(testSchedule.build());
        assertThat(hashValue).isEqualTo(origHashValue);
    }
}
