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
package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HbarFeeAssessorTest {
    private final List<AssessedCustomFeeWrapper> accumulator = new ArrayList<>();

    @Mock private BalanceChange payerChange;
    @Mock private BalanceChange collectorChange;
    @Mock private BalanceChangeManager balanceChangeManager;

    private HbarFeeAssessor subject = new HbarFeeAssessor();

    @Test
    void updatesExistingChangesIfPresent() {
        // setup:
        final var expectedFee =
                new AssessedCustomFeeWrapper(hbarFeeCollector, amountOfHbarFee, effPayerNums);

        given(balanceChangeManager.changeFor(payer, Id.MISSING_ID)).willReturn(payerChange);
        given(balanceChangeManager.changeFor(feeCollector, Id.MISSING_ID))
                .willReturn(collectorChange);

        // when:
        subject.assess(payer, hbarFee, balanceChangeManager, accumulator, false);

        // then:
        verify(payerChange).aggregateUnits(-amountOfHbarFee);
        verify(payerChange)
                .setCodeForInsufficientBalance(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
        // and:
        verify(collectorChange).aggregateUnits(+amountOfHbarFee);
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedFee, accumulator.get(0));
    }

    @Test
    void addsNewChangesIfNotPresent() {
        // given:
        final var expectedPayerChange = BalanceChange.hbarCustomFeeAdjust(payer, -amountOfHbarFee);
        expectedPayerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        // when:
        subject.assess(payer, hbarFee, balanceChangeManager, accumulator, false);

        // then:
        verify(balanceChangeManager).includeChange(expectedPayerChange);
        verify(balanceChangeManager)
                .includeChange(BalanceChange.hbarCustomFeeAdjust(feeCollector, +amountOfHbarFee));
    }

    private final long amountOfHbarFee = 100_000L;
    private final Id payer = new Id(0, 0, 2);
    private final AccountID[] effPayerNums =
            new AccountID[] {AccountID.newBuilder().setAccountNum(2L).build()};
    private final Id feeCollector = new Id(1, 2, 3);
    private final EntityId hbarFeeCollector = feeCollector.asEntityId();
    private final FcCustomFee hbarFee =
            FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector, false);
}
