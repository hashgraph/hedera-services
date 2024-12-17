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

package com.hedera.node.app.fees;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.OneOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FeeCalculatorImplTest {
    @Mock
    private CongestionMultipliers congestionMultipliers;

    private FeeData feeData;

    @Mock
    private TransactionBody txnBody;

    @BeforeEach
    void setUp() {
        feeData = new FeeData(FeeComponents.DEFAULT, FeeComponents.DEFAULT, FeeComponents.DEFAULT, SubType.DEFAULT);
    }

    @Test
    void canCreateFeeCalculator() {
        given(txnBody.data()).willReturn(new OneOf<>(TransactionBody.DataOneOfType.CRYPTO_CREATE_ACCOUNT, null));
        given(txnBody.memo()).willReturn("Test");
        given(txnBody.transactionIDOrThrow())
                .willReturn(
                        TransactionID.newBuilder().accountID(AccountID.DEFAULT).build());
        var calculator = new FeeCalculatorImpl(
                txnBody,
                Key.DEFAULT,
                0,
                0,
                feeData,
                ExchangeRate.DEFAULT,
                false,
                congestionMultipliers,
                new ReadableStoreFactory(new FakeState()));
        assertNotNull(calculator);

        calculator = new FeeCalculatorImpl(
                feeData,
                new ExchangeRate(0, 0, null),
                congestionMultipliers,
                new ReadableStoreFactory(new FakeState()),
                HederaFunctionality.CONTRACT_CALL);
        assertNotNull(calculator);
    }

    @Test
    void willFaiWithInvalidTransactionBody() {
        given(txnBody.data()).willReturn(new OneOf<>(TransactionBody.DataOneOfType.UNSET, null));
        given(txnBody.memo()).willReturn("Test");

        assertThrows(
                IllegalStateException.class,
                () -> new FeeCalculatorImpl(
                        txnBody,
                        Key.DEFAULT,
                        0,
                        0,
                        feeData,
                        ExchangeRate.DEFAULT,
                        false,
                        congestionMultipliers,
                        new ReadableStoreFactory(new FakeState())));
    }

    @Test
    void willReturnMultiplier() {
        var storeFactory = new ReadableStoreFactory(new FakeState());
        var calculator = new FeeCalculatorImpl(
                feeData,
                new ExchangeRate(0, 0, null),
                congestionMultipliers,
                storeFactory,
                HederaFunctionality.CONTRACT_CALL);

        calculator.getCongestionMultiplier();
        verify(congestionMultipliers).maxCurrentMultiplier(any(TransactionInfo.class), eq(storeFactory));
    }
}
