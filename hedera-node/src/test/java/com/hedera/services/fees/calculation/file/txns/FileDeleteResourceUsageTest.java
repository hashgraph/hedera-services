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
package com.hedera.services.fees.calculation.file.txns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileDeleteResourceUsageTest {
    private SigValueObj sigUsage;
    private FileFeeBuilder usageEstimator;
    private FileDeleteResourceUsage subject;

    private TransactionBody nonFileDeleteTxn;
    private TransactionBody fileDeleteTxn;

    @BeforeEach
    void setup() throws Throwable {
        fileDeleteTxn = mock(TransactionBody.class);
        given(fileDeleteTxn.hasFileDelete()).willReturn(true);

        nonFileDeleteTxn = mock(TransactionBody.class);
        given(nonFileDeleteTxn.hasFileDelete()).willReturn(false);

        sigUsage = mock(SigValueObj.class);
        usageEstimator = mock(FileFeeBuilder.class);

        subject = new FileDeleteResourceUsage(usageEstimator);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(fileDeleteTxn));
        assertFalse(subject.applicableTo(nonFileDeleteTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        // when:
        subject.usageGiven(fileDeleteTxn, sigUsage, null);

        // then:
        verify(usageEstimator).getFileDeleteTxFeeMatrices(fileDeleteTxn, sigUsage);
    }
}
