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
package com.hedera.services.files.sysfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyCallbacksTest {
    ExchangeRates curMidnightRates;

    @Mock FeeCalculator fees;
    @Mock HbarCentExchange exchange;
    @Mock Supplier<ExchangeRates> midnightRates;

    CurrencyCallbacks subject;

    @BeforeEach
    void setUp() {
        subject = new CurrencyCallbacks(fees, exchange, midnightRates);
    }

    @Test
    void ratesCbAsExpectedWithExistingMidnightRates() {
        // setup:
        curMidnightRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);
        // and:
        var rates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var grpcRates = rates.toGrpc();

        given(midnightRates.get()).willReturn(curMidnightRates);

        // when:
        subject.exchangeRatesCb().accept(grpcRates);

        // then:
        verify(exchange).updateRates(grpcRates);
        assertNotEquals(curMidnightRates, rates);
    }

    @Test
    void ratesCbAsExpectedWithMissingMidnightRates() {
        // setup:
        curMidnightRates = new ExchangeRates();
        // and:
        var rates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var grpcRates = rates.toGrpc();

        given(midnightRates.get()).willReturn(curMidnightRates);

        // when:
        subject.exchangeRatesCb().accept(grpcRates);

        // then:
        verify(exchange).updateRates(grpcRates);
        assertEquals(curMidnightRates, rates);
    }

    @Test
    void feesCbJustDelegates() {
        // when:
        subject.feeSchedulesCb().accept(CurrentAndNextFeeSchedule.getDefaultInstance());

        // then:
        verify(fees).init();
    }
}
