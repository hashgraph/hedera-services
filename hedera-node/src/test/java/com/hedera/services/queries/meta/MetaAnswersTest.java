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
package com.hedera.services.queries.meta;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.mock;

import org.junit.jupiter.api.Test;

class MetaAnswersTest {
    GetExecTimeAnswer execTime;
    GetTxnRecordAnswer txnRecord;
    GetTxnReceiptAnswer txnReceipt;
    GetVersionInfoAnswer versionInfo;
    GetFastTxnRecordAnswer fastTxnRecord;
    MetaAnswers subject;
    GetAccountDetailsAnswer accountDetails;

    @Test
    void hasExpectedAnswers() {
        // setup:
        execTime = mock(GetExecTimeAnswer.class);
        txnRecord = mock(GetTxnRecordAnswer.class);
        txnReceipt = mock(GetTxnReceiptAnswer.class);
        versionInfo = mock(GetVersionInfoAnswer.class);
        fastTxnRecord = mock(GetFastTxnRecordAnswer.class);
        accountDetails = mock(GetAccountDetailsAnswer.class);

        // given:
        subject =
                new MetaAnswers(
                        execTime,
                        txnRecord,
                        txnReceipt,
                        versionInfo,
                        fastTxnRecord,
                        accountDetails);

        // then:
        assertSame(txnRecord, subject.getTxnRecord());
        assertSame(txnReceipt, subject.getTxnReceipt());
        assertSame(versionInfo, subject.getVersionInfo());
        assertSame(fastTxnRecord, subject.getFastTxnRecord());
        assertSame(execTime, subject.getExecTime());
        assertSame(accountDetails, subject.getAccountDetails());
    }
}
