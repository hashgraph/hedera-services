package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduledTransactionStatusOrderResultFactoryTest {
    private ScheduledTransactionStatusOrderResultFactory subject;
    private boolean inHandleTxnDynamicContext = true;
    private TransactionID txnId = TransactionID.getDefaultInstance();

    @Test
    public void returnsNormalSummaryForValidOrder() {
        // given:
        subject = new ScheduledTransactionStatusOrderResultFactory(false);
        ScheduledTransactionOrderResult<SignatureStatus> summary = subject.forValidOrder("tx_body".getBytes());

        // expect:
        assertTrue(summary.hasKnownOrder());
    }

    @Test
    public void reportsMissingSchedule() {
        // setup:
        ScheduleID missing = IdUtils.asSchedule("1.2.3");
        SignatureStatus expectedError = new SignatureStatus(
                SignatureStatusCode.INVALID_SCHEDULE_ID, ResponseCodeEnum.INVALID_SCHEDULE_ID,
                inHandleTxnDynamicContext, txnId, missing);

        // given:
        subject = new ScheduledTransactionStatusOrderResultFactory(inHandleTxnDynamicContext);
        ScheduledTransactionOrderResult<SignatureStatus> summary = subject.forMissingSchedule(missing, txnId);
        SignatureStatus error = summary.getErrorReport();

        // expect:
        assertEquals(expectedError.toLogMessage(), error.toLogMessage());
    }

}
