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

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileCreateResourceUsageTest {
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj svo = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    private FileOpsUsage fileOpsUsage;
    private FileCreateResourceUsage subject;

    private TransactionBody nonFileCreateTxn;
    private TransactionBody fileCreateTxn;

    @BeforeEach
    void setup() throws Throwable {
        fileCreateTxn = mock(TransactionBody.class);
        given(fileCreateTxn.hasFileCreate()).willReturn(true);

        nonFileCreateTxn = mock(TransactionBody.class);
        given(nonFileCreateTxn.hasFileCreate()).willReturn(false);

        fileOpsUsage = mock(FileOpsUsage.class);

        subject = new FileCreateResourceUsage(fileOpsUsage);
    }

    @Test
    void recognizesApplicability() {
        // expect:
        assertTrue(subject.applicableTo(fileCreateTxn));
        assertFalse(subject.applicableTo(nonFileCreateTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());

        // when:
        subject.usageGiven(fileCreateTxn, svo, null);

        // then:
        verify(fileOpsUsage).fileCreateUsage(fileCreateTxn, sigUsage);
    }
}
