/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomSchedulesManagerTest {
    @Mock private CustomFeeSchedules customFeeSchedules;

    private CustomSchedulesManager subject;

    @BeforeEach
    void setUp() {
        subject = new CustomSchedulesManager(customFeeSchedules);
    }

    @Test
    void usesDelegateForMissing() {
        given(customFeeSchedules.lookupMetaFor(a)).willReturn(aMeta);

        // when:
        final var ans = subject.managedSchedulesFor(a);

        // then:
        assertSame(aMeta, ans);
    }

    @Test
    void reusesExtantScheduleIfPresent() {
        given(customFeeSchedules.lookupMetaFor(a))
                .willReturn(aMeta)
                .willThrow(AssertionError.class);

        // when:
        final var firstAns = subject.managedSchedulesFor(a);
        final var secondAns = subject.managedSchedulesFor(a);

        // then:
        assertSame(firstAns, secondAns);
    }

    @Test
    void enumeratesAllManagedSchedules() {
        given(customFeeSchedules.lookupMetaFor(a)).willReturn(aMeta);
        given(customFeeSchedules.lookupMetaFor(b)).willReturn(bMeta);

        // when:
        subject.managedSchedulesFor(a);
        subject.managedSchedulesFor(b);
        // and:
        final var all = subject.metaUsed();

        // then:
        assertEquals(2, all.size());
        // and:
        final var first = all.get(0);
        assertSame(aMeta, first);
        // and:
        final var second = all.get(1);
        assertEquals(bMeta, second);
    }

    private final long amountOfHbarFee = 100_000L;
    private final Id hbarFeeCollectorId = new Id(1, 2, 3);
    private final EntityId hbarFeeCollector = hbarFeeCollectorId.asEntityId();
    private final FcCustomFee hbarFee =
            FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector, false);
    private final long amountOfHtsFee = 100_000L;
    private final Id htsFeeCollectorId = new Id(1, 2, 3);
    private final EntityId feeDenom = new EntityId(6, 6, 6);
    private final EntityId htsFeeCollector = htsFeeCollectorId.asEntityId();
    private final FcCustomFee htsFee =
            FcCustomFee.fixedFee(amountOfHtsFee, feeDenom, htsFeeCollector, false);
    final Id a = new Id(1, 2, 3);
    final Id aTreasury = new Id(2, 2, 3);
    final Id b = new Id(2, 3, 4);
    final Id bTreasury = new Id(3, 3, 4);
    final List<FcCustomFee> aSchedule = List.of(hbarFee);
    final List<FcCustomFee> bSchedule = List.of(htsFee);
    final CustomFeeMeta aMeta = new CustomFeeMeta(a, aTreasury, aSchedule);
    final CustomFeeMeta bMeta = new CustomFeeMeta(b, bTreasury, bSchedule);
}
