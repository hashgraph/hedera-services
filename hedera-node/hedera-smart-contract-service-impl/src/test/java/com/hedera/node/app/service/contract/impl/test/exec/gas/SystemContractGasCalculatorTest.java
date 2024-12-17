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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.CanonicalDispatchPrices;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import java.util.function.ToLongBiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemContractGasCalculatorTest {
    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private ToLongBiFunction<TransactionBody, AccountID> feeCalculator;

    @Mock
    private CanonicalDispatchPrices dispatchPrices;

    private SystemContractGasCalculator subject;

    @BeforeEach
    void setUp() {
        subject = new SystemContractGasCalculator(tinybarValues, dispatchPrices, feeCalculator);
    }

    @Test
    void returnsMinimumGasCostForViews() {
        assertEquals(100L, subject.viewGasRequirement());
    }

    @Test
    void computesCanonicalDispatchType() {
        given(dispatchPrices.canonicalPriceInTinycents(DispatchType.APPROVE)).willReturn(123L);
        given(tinybarValues.asTinybars(123L)).willReturn(321L);
        assertEquals(321L, subject.canonicalPriceInTinycents(DispatchType.APPROVE));
    }

    @Test
    void computesCanonicalDispatch() {
        given(feeCalculator.applyAsLong(TransactionBody.DEFAULT, AccountID.DEFAULT))
                .willReturn(123L);
        assertEquals(123L, subject.feeCalculatorPriceInTinyBars(TransactionBody.DEFAULT, AccountID.DEFAULT));
    }

    @Test
    void computesGasCostInTinybars() {
        given(tinybarValues.childTransactionTinybarGasPrice()).willReturn(2L);
        assertEquals(6L, subject.gasCostInTinybars(3L));
    }

    @Test
    void delegatesTopLevelGasPrice() {
        given(tinybarValues.topLevelTinybarGasPrice()).willReturn(123L);
        assertEquals(123L, subject.topLevelGasPrice());
    }
}
