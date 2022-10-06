/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation.contract.txns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractCreateResourceUsageTest {
    private SigValueObj sigUsage;
    private SmartContractFeeBuilder usageEstimator;
    private ContractCreateResourceUsage subject;

    private TransactionBody nonContractCreateTxn;
    private TransactionBody contractCreateTxn;

    @BeforeEach
    void setup() throws Throwable {
        contractCreateTxn = mock(TransactionBody.class);
        given(contractCreateTxn.hasContractCreateInstance()).willReturn(true);

        nonContractCreateTxn = mock(TransactionBody.class);
        given(nonContractCreateTxn.hasContractCreateInstance()).willReturn(false);

        sigUsage = mock(SigValueObj.class);
        usageEstimator = mock(SmartContractFeeBuilder.class);

        subject = new ContractCreateResourceUsage(usageEstimator);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(contractCreateTxn));
        assertFalse(subject.applicableTo(nonContractCreateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        // when:
        subject.usageGiven(contractCreateTxn, sigUsage, null);

        // then:
        verify(usageEstimator).getContractCreateTxFeeMatrices(contractCreateTxn, sigUsage);
    }
}
