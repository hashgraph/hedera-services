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

import static com.hedera.services.grpc.marshalling.AdjustmentUtils.adjustedChange;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdjustmentUtilsTest {
    @Mock private BalanceChangeManager changeManager;

    @Test
    void includesNewHtsChange() {
        final var account = new Id(1, 2, 3);
        final var denom = new Id(2, 3, 4);
        final var chargingToken = new Id(3, 4, 5);
        final var amount = 123L;
        final var expectedChange = BalanceChange.tokenAdjust(account, denom, amount);

        final var change = adjustedChange(account, chargingToken, denom, amount, changeManager);

        Assertions.assertEquals(expectedChange, change);
        verify(changeManager).includeChange(expectedChange);
    }

    @Test
    void alsoIncludesAnyHtsDebit() {
        final var account = new Id(1, 2, 3);
        final var denom = new Id(2, 3, 4);
        final var chargingToken = new Id(3, 4, 5);
        final var amount = -123L;
        final var expectedChange = BalanceChange.tokenAdjust(account, denom, amount);

        final var change = adjustedChange(account, chargingToken, denom, amount, changeManager);

        Assertions.assertEquals(expectedChange, change);
        verify(changeManager, never()).changeFor(account, denom);
        verify(changeManager).includeChange(expectedChange);
    }

    @Test
    void includesExemptHtsDebitWhenSelfDenominated() {
        final var account = new Id(1, 2, 3);
        final var denom = new Id(2, 3, 4);
        final var amount = -123L;
        final var expectedChange = BalanceChange.tokenAdjust(account, denom, amount);
        expectedChange.setExemptFromCustomFees(true);

        final var change = adjustedChange(account, denom, denom, amount, changeManager);

        Assertions.assertEquals(expectedChange, change);
        verify(changeManager, never()).changeFor(account, denom);
        verify(changeManager).includeChange(expectedChange);
    }
}
